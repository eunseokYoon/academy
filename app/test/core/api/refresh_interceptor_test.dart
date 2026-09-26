import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/auth_interceptor.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/api/refresh_interceptor.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

void main() {
  const okBody = {'success': true, 'data': null, 'error': null};
  const unauthorized = {
    'success': false,
    'data': null,
    'error': {'code': 'TOKEN_EXPIRED', 'message': '인증이 만료되었습니다. 다시 로그인해 주세요.'},
  };
  Map<String, dynamic> refreshed(String token) => {
    'success': true,
    'data': {'accessToken': token},
    'error': null,
  };

  late TokenStore tokens;
  late CookieStore cookies;
  late CookieJar jar;
  late int expiredCalls;

  setUp(() {
    tokens = TokenStore(InMemoryKeyValueStore());
    cookies = CookieStore(
      InMemoryKeyValueStore(),
      Uri.parse('https://example.test/api/auth'),
    );
    jar = CookieJar();
    expiredCalls = 0;
  });

  /// main 은 업무 요청용, plain 은 리프레시·재시도용이다.
  (Dio, FakeAdapter, FakeAdapter) build({
    required List<FakeReply> main,
    required List<FakeReply> plain,
  }) {
    final mainAdapter = FakeAdapter(replies: main);
    final plainAdapter = FakeAdapter(replies: plain);

    final plainDio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = plainAdapter;

    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = mainAdapter
      ..interceptors.add(AuthInterceptor(tokens))
      ..interceptors.add(
        RefreshInterceptor(
          plain: plainDio,
          tokens: tokens,
          cookies: cookies,
          jar: jar,
          onSessionExpired: () async => expiredCalls++,
        ),
      );

    return (dio, mainAdapter, plainAdapter);
  }

  test('401이면 리프레시하고 새 토큰으로 재시도한다', () async {
    await tokens.write('old');
    final (dio, _, plainAdapter) = build(
      main: [const FakeReply(statusCode: 401, body: unauthorized)],
      plain: [
        FakeReply(statusCode: 200, body: refreshed('new')),
        const FakeReply(statusCode: 200, body: okBody),
      ],
    );

    final res = await dio.get<Map<String, dynamic>>('/api/student/home');

    expect(res.statusCode, 200);
    expect(await tokens.read(), 'new');
    // 리프레시 1회 + 재시도 1회
    expect(plainAdapter.received.length, 2);
    expect(plainAdapter.received[0].path, '/api/auth/refresh');
    expect(plainAdapter.received[1].path, '/api/student/home');
    expect(plainAdapter.received[1].headers['Authorization'], 'Bearer new');
    expect(expiredCalls, 0);
  });

  test('동시 401 다섯 개가 리프레시를 한 번만 부른다', () async {
    // QueuedInterceptor 가 onError 를 직렬화하고, 두 번째 이후는
    // "내가 보낸 토큰 != 지금 저장된 토큰" 을 보고 리프레시를 건너뛴다.
    // 이게 없으면 리프레시가 5번 나가고 그중 4개가 이미 회전된 토큰으로 실패한다.
    await tokens.write('old');
    final (dio, _, plainAdapter) = build(
      main: List.generate(
        5,
        (_) => const FakeReply(statusCode: 401, body: unauthorized),
      ),
      plain: [
        FakeReply(statusCode: 200, body: refreshed('new')),
        ...List.generate(
          5,
          (_) => const FakeReply(statusCode: 200, body: okBody),
        ),
      ],
    );

    await Future.wait([
      for (var i = 0; i < 5; i++)
        dio.get<Map<String, dynamic>>('/api/student/p$i'),
    ]);

    final refreshCalls = plainAdapter.received
        .where((r) => r.path == '/api/auth/refresh')
        .length;
    expect(refreshCalls, 1);
    expect(await tokens.read(), 'new');
  });

  test('리프레시가 실패하면 토큰·쿠키를 비우고 세션 만료를 알린다', () async {
    await tokens.write('old');
    final (dio, _, _) = build(
      main: [const FakeReply(statusCode: 401, body: unauthorized)],
      plain: [const FakeReply(statusCode: 401, body: unauthorized)],
    );

    await expectLater(
      dio.get<Map<String, dynamic>>('/api/student/home'),
      throwsA(isA<DioException>()),
    );

    expect(await tokens.read(), isNull);
    expect(expiredCalls, 1);
  });

  test('리프레시 성공 후 재시도가 실패해도 세션을 비우지 않는다', () async {
    // 실사례: 비밀번호 변경 화면에서 현재 비밀번호를 한 번 틀리면
    // (백엔드가 INVALID_CREDENTIALS 를 401 로 준다) 리프레시 자체는 쿠키가
    // 멀쩡하니 성공하고, 재시도가 같은 틀린 비밀번호로 다시 401 을 받는다.
    // 리프레시와 재시도를 한 try 로 묶으면 이 401 도 "세션 만료"로 오인해
    // 토큰을 비우고 로그인 화면으로 튕긴다 — 정작 필요한 건 에러 메시지뿐인데.
    await tokens.write('old');
    final (dio, _, plainAdapter) = build(
      main: [const FakeReply(statusCode: 401, body: unauthorized)],
      plain: [
        FakeReply(statusCode: 200, body: refreshed('new')),
        const FakeReply(
          statusCode: 401,
          body: {
            'success': false,
            'data': null,
            'error': {
              'code': 'INVALID_CREDENTIALS',
              'message': '아이디 또는 비밀번호가 올바르지 않습니다.',
            },
          },
        ),
      ],
    );

    await expectLater(
      dio.patch<Map<String, dynamic>>('/api/auth/password'),
      throwsA(isA<DioException>()),
    );

    // 리프레시는 성공했으니 새 토큰은 남아 있어야 한다.
    expect(await tokens.read(), 'new');
    // 실패한 것은 재시도한 요청 하나뿐이다 — 세션이 만료된 게 아니다.
    expect(expiredCalls, 0);
    expect(plainAdapter.received.length, 2);
  });

  test('401이 아닌 에러는 그대로 통과한다', () async {
    await tokens.write('old');
    final (dio, _, plainAdapter) = build(
      main: [
        const FakeReply(
          statusCode: 409,
          body: {
            'success': false,
            'data': null,
            'error': {'code': 'DUE_DATE_PASSED', 'message': '마감 시간이 지났습니다.'},
          },
        ),
      ],
      plain: [],
    );

    await expectLater(
      dio.post<Map<String, dynamic>>('/api/student/homeworks/1/submit'),
      throwsA(isA<DioException>()),
    );

    // 리프레시를 부르지 않았다
    expect(plainAdapter.received, isEmpty);
    expect(expiredCalls, 0);
  });
}
