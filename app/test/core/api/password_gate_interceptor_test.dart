import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/api/password_gate_interceptor.dart';

void main() {
  Map<String, dynamic> forbidden(String code, String message) => {
        'success': false,
        'data': null,
        'error': {'code': code, 'message': message},
      };

  late int calls;

  Dio build(FakeReply reply) {
    calls = 0;
    return Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = FakeAdapter(replies: [reply])
      ..interceptors.add(PasswordGateInterceptor(() => calls++));
  }

  test('PASSWORD_CHANGE_REQUIRED면 콜백을 부른다', () async {
    // 초기 비밀번호가 전원 0000 이라, 바꾸기 전에는 3경로 외 전부 403 이다.
    final dio = build(FakeReply(
      statusCode: 403,
      body: forbidden('PASSWORD_CHANGE_REQUIRED', '비밀번호를 먼저 변경해 주세요.'),
    ));

    await expectLater(
      dio.get<Map<String, dynamic>>('/api/student/home'),
      throwsA(isA<DioException>()),
    );
    expect(calls, 1);
  });

  test('다른 403에는 콜백을 부르지 않는다', () async {
    // ROLE_NOT_ALLOWED 는 경로 접두사 위반이다. 앱에서 나면 코드 버그이고,
    // 비밀번호 화면으로 보내면 원인이 가려진다.
    final dio = build(FakeReply(
      statusCode: 403,
      body: forbidden('ROLE_NOT_ALLOWED', '접근 권한이 없습니다.'),
    ));

    await expectLater(
      dio.get<Map<String, dynamic>>('/api/teacher/students'),
      throwsA(isA<DioException>()),
    );
    expect(calls, 0);
  });

  test('401에는 콜백을 부르지 않는다', () async {
    final dio = build(FakeReply(
      statusCode: 401,
      body: forbidden('TOKEN_EXPIRED', '인증이 만료되었습니다. 다시 로그인해 주세요.'),
    ));

    await expectLater(
      dio.get<Map<String, dynamic>>('/api/student/home'),
      throwsA(isA<DioException>()),
    );
    expect(calls, 0);
  });

  test('엔벌로프가 아닌 403도 앱을 깨뜨리지 않는다', () async {
    // nginx 가 403 을 HTML 로 돌려주는 경우다
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter =
          FakeAdapter(replies: [const FakeReply(statusCode: 403, body: {})])
      ..interceptors.add(PasswordGateInterceptor(() => calls++));
    calls = 0;

    await expectLater(
      dio.get<Map<String, dynamic>>('/api/student/home'),
      throwsA(isA<DioException>()),
    );
    expect(calls, 0);
  });
}
