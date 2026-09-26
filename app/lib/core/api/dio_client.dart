import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';

import '../storage/token_store.dart';
import 'auth_interceptor.dart';

/// Dio를 **조립만** 한다. 로직을 여기 넣지 마라 — 인터셉터가 각자 자기 파일에 있다.
class DioClient {
  const DioClient._();

  /// `main.dart`의 `plain`(리프레시 전용 Dio)도 이 값을 그대로 써야 한다.
  /// `RefreshInterceptor`가 `QueuedInterceptor`라 리프레시 하나가 멈추면
  /// 뒤에 줄 선 401 복구가 전부 막힌다 — 타임아웃이 없으면 영원히 막힌다.
  static const connectTimeout = Duration(seconds: 10);
  static const receiveTimeout = Duration(seconds: 20);

  static Dio build({
    required String baseUrl,
    required TokenStore tokens,
    required CookieJar jar,
    List<Interceptor> extra = const [],
  }) {
    final dio = Dio(
      BaseOptions(
        baseUrl: baseUrl,
        connectTimeout: connectTimeout,
        receiveTimeout: receiveTimeout,
        // 401·403·409를 예외가 아니라 응답으로 받아서 인터셉터가 다루게 한다면
        // 모든 화면이 상태 코드를 보게 된다. 그러지 않는다 — Dio가 던지고
        // 인터셉터가 onError에서 처리한다.
        headers: {'Accept': 'application/json'},
      ),
    );

    // 순서가 중요하다.
    // 1) 쿠키 — 리프레시 쿠키를 싣고 Set-Cookie를 받는다. 가장 바깥이어야 한다
    // 2) 토큰 부착
    // 3) extra — 401 리프레시와 비밀번호 게이트가 여기로 들어온다(Task 6·7)
    dio.interceptors.add(CookieManager(jar));
    dio.interceptors.add(AuthInterceptor(tokens));
    dio.interceptors.addAll(extra);

    return dio;
  }
}
