import 'package:dio/dio.dart';

import '../storage/token_store.dart';

/// 액세스 토큰을 붙인다. **없으면 붙이지 않는다** — 로그인·가입은 비로그인
/// 호출이고, 빈 `Bearer `를 보내면 서버가 401로 받는다.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokens);

  final TokenStore _tokens;

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _tokens.read();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }
}
