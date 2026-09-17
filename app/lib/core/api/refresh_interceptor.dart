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
  RefreshInterceptor({
    required Dio plain,
    required TokenStore tokens,
    required CookieStore cookies,
    required CookieJar jar,
    required Future<void> Function() onSessionExpired,
  })
      // named 매개변수에 private 이름(`this._plain`)을 쓰는 것을 Dart가 금지하므로,
      // 필드를 private으로 두려면 이 방식뿐이다. 필드를 공개로 바꿔 린트를
      // 만족시키지 마라 — `plain`은 이 클래스가 숨기려고 존재하는, 인터셉터 없는
      // Dio다. 공개되면 인터셉터 목록을 훑어 꺼내서 인증도 401 복구도 없이
      // 요청을 보낼 수 있다.
      // ignore: prefer_initializing_formals
      : _plain = plain,
        // ignore: prefer_initializing_formals
        _tokens = tokens,
        // ignore: prefer_initializing_formals
        _cookies = cookies,
        // ignore: prefer_initializing_formals
        _jar = jar,
        // ignore: prefer_initializing_formals
        _onSessionExpired = onSessionExpired;

  final Dio _plain;
  final TokenStore _tokens;
  final CookieStore _cookies;
  final CookieJar _jar;
  final Future<void> Function() _onSessionExpired;

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
    final current = await _tokens.read();

    // 앞의 누군가가 이미 갱신했다. 리프레시 없이 재시도만 한다.
    if (current != null && sentWith != 'Bearer $current') {
      try {
        handler.resolve(await _retry(err.requestOptions, current));
      } on DioException catch (_) {
        handler.next(err);
      }
      return;
    }

    final String token;
    try {
      final res = await _plain.post<Map<String, dynamic>>('/api/auth/refresh');
      token = unwrap<String>(
        res.data ?? const {},
        res.statusCode,
        (data) => (data as Map<String, dynamic>)['accessToken'] as String,
      );
      await _tokens.write(token);
      // 리프레시가 쿠키를 회전시키면 Set-Cookie 가 온다. 새 값을 남겨야 한다.
      await _cookies.persist(_jar);
    } catch (_) {
      // 리프레시 토큰이 죽었다. 재로그인 외에 길이 없다.
      await _tokens.clear();
      await _cookies.clear();
      await _jar.deleteAll();
      await _onSessionExpired();
      handler.next(err);
      return;
    }

    try {
      handler.resolve(await _retry(err.requestOptions, token));
    } on DioException catch (_) {
      // **리프레시는 성공했다. 상태를 비우지 마라** — 실패한 것은 이 요청뿐이다.
      // 비우면 비밀번호 화면에서 현재 비밀번호를 한 번 틀리는 것만으로
      // (백엔드가 INVALID_CREDENTIALS를 401로 준다) 강제 로그아웃된다.
      handler.next(err);
    }
  }

  Future<Response<dynamic>> _retry(RequestOptions options, String token) {
    // 원 요청을 그대로 다시 보낸다. plain 을 쓰므로 이 인터셉터를 다시 타지 않는다.
    options.headers['Authorization'] = 'Bearer $token';
    return _plain.fetch<dynamic>(options);
  }
}
