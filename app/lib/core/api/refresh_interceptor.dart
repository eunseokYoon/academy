import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';

import '../storage/cookie_store.dart';
import '../storage/token_store.dart';
import 'api_response.dart';

/// 401을 리프레시로 복구한다.
///
/// **`QueuedInterceptor`를 쓰는 것이 핵심이다.** 기본 `Interceptor`는 동시에
/// 실행되므로, 화면 하나가 API 다섯 개를 동시에 불러 전부 401이 나면 리프레시가
/// 다섯 번 나가고 그중 넷이 이미 회전된 토큰으로 실패한다. `QueuedInterceptor`는
/// `onError`를 직렬화한다.
///
/// 직렬화만으로는 부족하다 — 줄을 선 두 번째 요청도 자기 차례에 리프레시를
/// 부르려 한다. 그래서 **자기가 보낸 토큰과 지금 저장된 토큰을 비교한다.**
/// 다르면 앞의 누군가가 이미 갱신한 것이므로 재시도만 한다.
///
/// `plain`은 **이 인터셉터가 붙지 않은 Dio**다. 두 가지를 보장한다 —
/// 리프레시 호출이 401이어도 재귀하지 않고, 재시도가 정확히 한 번이다.
/// 쿠키 자는 공유해야 한다. 리프레시 토큰이 쿠키로 실려 나가기 때문이다.
class RefreshInterceptor extends QueuedInterceptor {
  // 필드를 초기화 폼(`this.plain`)으로 못 받는 이유: 생성자의 공개 이름
  // (plain·tokens·cookies·jar·onSessionExpired)은 Task 14가 그대로 부르는
  // 계약이다. 필드를 관례대로 `_plain`처럼 private으로 두고 초기화 폼을 쓰면
  // 매개변수 외부 이름도 `_plain`이 되어 다른 라이브러리(Task 14, 이 테스트
  // 파일)에서 이름 있는 인자로 부를 수 없게 된다 — 직접 검증했다. 그래서
  // 필드를 공개로 두고 초기화 폼을 쓴다. 호출자가 넘긴 바로 그 객체라
  // 공개해도 새로 드러나는 것은 없다.
  RefreshInterceptor({
    required this.plain,
    required this.tokens,
    required this.cookies,
    required this.jar,
    required this.onSessionExpired,
  });

  final Dio plain;
  final TokenStore tokens;
  final CookieStore cookies;
  final CookieJar jar;
  final Future<void> Function() onSessionExpired;

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    if (err.response?.statusCode != 401) {
      handler.next(err);
      return;
    }

    final sentWith = err.requestOptions.headers['Authorization'] as String?;
    final current = await tokens.read();

    // 앞의 누군가가 이미 갱신했다. 리프레시 없이 재시도만 한다.
    if (current != null && sentWith != 'Bearer $current') {
      try {
        handler.resolve(await _retry(err.requestOptions, current));
      } on DioException catch (_) {
        handler.next(err);
      }
      return;
    }

    try {
      final res = await plain.post<Map<String, dynamic>>('/api/auth/refresh');
      final token = unwrap<String>(
        res.data ?? const {},
        res.statusCode,
        (data) => (data as Map<String, dynamic>)['accessToken'] as String,
      );
      await tokens.write(token);
      // 리프레시가 쿠키를 회전시키면 Set-Cookie 가 온다. 새 값을 남겨야 한다.
      await cookies.persist(jar);
      handler.resolve(await _retry(err.requestOptions, token));
    } catch (_) {
      // 리프레시 토큰이 죽었다. 재로그인 외에 길이 없다.
      await tokens.clear();
      await cookies.clear();
      await jar.deleteAll();
      await onSessionExpired();
      handler.next(err);
    }
  }

  Future<Response<dynamic>> _retry(RequestOptions options, String token) {
    // 원 요청을 그대로 다시 보낸다. plain 을 쓰므로 이 인터셉터를 다시 타지 않는다.
    options.headers['Authorization'] = 'Bearer $token';
    return plain.fetch<dynamic>(options);
  }
}
