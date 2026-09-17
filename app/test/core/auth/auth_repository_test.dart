import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/user_role.dart';

void main() {
  AuthRepository repoWith(FakeAdapter adapter) {
    final dio = Dio(BaseOptions(
      baseUrl: 'https://example.test',
      validateStatus: (_) => true,
    ))
      ..httpClientAdapter = adapter;
    return AuthRepository(dio);
  }

  test('로그인이 액세스 토큰과 사용자를 돌려준다', () async {
    // 리프레시 토큰은 본문에 없다 — 쿠키로만 온다. 모델에 자리를 만들지 마라.
    final adapter = FakeAdapter(replies: const [
      FakeReply(statusCode: 200, body: {
        'success': true,
        'data': {
          'accessToken': 'at-1',
          'user': {
            'id': 7,
            'name': '김하늘',
            'role': 'STUDENT',
            'mustChangePassword': true,
          },
        },
        'error': null,
      }),
    ]);

    final res = await repoWith(adapter)
        .login(loginId: '01012345678', password: '0000');

    expect(res.accessToken, 'at-1');
    expect(res.user.id, 7);
    expect(res.user.name, '김하늘');
    expect(res.user.role, UserRole.student);
    expect(res.user.mustChangePassword, isTrue);

    final sent = adapter.received.single;
    expect(sent.path, '/api/auth/login');
    expect(sent.data, {'loginId': '01012345678', 'password': '0000'});
  });

  test('자격증명이 틀리면 ApiException을 던진다', () async {
    final adapter = FakeAdapter(replies: const [
      FakeReply(statusCode: 401, body: {
        'success': false,
        'data': null,
        'error': {
          'code': 'INVALID_CREDENTIALS',
          'message': '아이디 또는 비밀번호가 올바르지 않습니다.',
        },
      }),
    ]);

    await expectLater(
      repoWith(adapter).login(loginId: '01012345678', password: 'wrong'),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'INVALID_CREDENTIALS')),
    );
  });

  test('me가 본인 번호까지 돌려준다', () async {
    // phone 은 본인 번호라 마스킹하지 않는다. 자기 번호를 자기가 보는 것이다.
    final adapter = FakeAdapter(replies: const [
      FakeReply(statusCode: 200, body: {
        'success': true,
        'data': {
          'id': 9,
          'name': '김하늘 학부모',
          'role': 'PARENT',
          'phone': '01098765432',
          'mustChangePassword': false,
        },
        'error': null,
      }),
    ]);

    final me = await repoWith(adapter).me();

    expect(me.role, UserRole.parent);
    expect(me.phone, '01098765432');
    expect(me.mustChangePassword, isFalse);
    expect(adapter.received.single.path, '/api/auth/me');
  });

  test('가입은 비밀번호를 보내지 않는다', () async {
    // 서버가 code 를 보고 학생·학부모를 판별한다. 사용자는 역할을 고르지 않고,
    // phone 이 로그인 아이디가 되며 초기 비밀번호는 0000 이다.
    final adapter = FakeAdapter(replies: const [
      FakeReply(statusCode: 200, body: {
        'success': true,
        'data': {'studentId': 7},
        'error': null,
      }),
    ]);

    await repoWith(adapter).signup(
      code: 'ABCD12',
      name: '김하늘',
      phone: '01012345678',
      parentPhone: '01098765432',
    );

    final sent = adapter.received.single;
    expect(sent.path, '/api/auth/signup');
    expect(sent.data, {
      'code': 'ABCD12',
      'name': '김하늘',
      'phone': '01012345678',
      'parentPhone': '01098765432',
    });
    expect((sent.data as Map).containsKey('password'), isFalse);
  });

  test('비밀번호 변경과 로그아웃은 data가 null인 성공을 받는다', () async {
    final adapter = FakeAdapter(replies: const [
      FakeReply(statusCode: 200, body: {'success': true, 'data': null, 'error': null}),
      FakeReply(statusCode: 200, body: {'success': true, 'data': null, 'error': null}),
    ]);
    final repo = repoWith(adapter);

    await repo.changePassword(currentPassword: '0000', newPassword: 'newpass1');
    await repo.logout();

    expect(adapter.received[0].path, '/api/auth/password');
    expect(adapter.received[0].method, 'PATCH');
    expect(adapter.received[1].path, '/api/auth/logout');
  });
}
