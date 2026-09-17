import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/auth_interceptor.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

void main() {
  const okBody = {'success': true, 'data': null, 'error': null};

  Dio dioWith(TokenStore tokens, FakeAdapter adapter) {
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'));
    dio.httpClientAdapter = adapter;
    dio.interceptors.add(AuthInterceptor(tokens));
    return dio;
  }

  test('토큰이 있으면 Authorization 헤더를 붙인다', () async {
    final tokens = TokenStore(InMemoryKeyValueStore());
    await tokens.write('abc');
    final adapter = FakeAdapter(
      replies: const [FakeReply(statusCode: 200, body: okBody)],
    );

    await dioWith(
      tokens,
      adapter,
    ).get<Map<String, dynamic>>('/api/student/home');

    expect(adapter.received.single.headers['Authorization'], 'Bearer abc');
  });

  test('토큰이 없으면 헤더를 붙이지 않는다', () async {
    // 로그인과 가입은 비로그인 호출이다. 빈 Bearer 를 보내면 401 이 난다.
    final adapter = FakeAdapter(
      replies: const [FakeReply(statusCode: 200, body: okBody)],
    );

    await dioWith(
      TokenStore(InMemoryKeyValueStore()),
      adapter,
    ).post<Map<String, dynamic>>('/api/auth/login');

    expect(
      adapter.received.single.headers.containsKey('Authorization'),
      isFalse,
    );
  });
}
