import 'package:dio/dio.dart';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/dio_client.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

void main() {
  AuthRepository repoWith(FakeAdapter adapter) {
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = adapter;
    return AuthRepository(dio);
  }

  test('로그인이 액세스 토큰과 사용자를 돌려준다', () async {
    // 리프레시 토큰은 본문에 없다 — 쿠키로만 온다. 모델에 자리를 만들지 마라.
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
          statusCode: 200,
          body: {
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
          },
        ),
      ],
    );

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
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
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
      repoWith(adapter).login(loginId: '01012345678', password: 'wrong'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.code,
          'code',
          'INVALID_CREDENTIALS',
        ),
      ),
    );
  });

  test('me가 본인 번호까지 돌려준다', () async {
    // phone 은 본인 번호라 마스킹하지 않는다. 자기 번호를 자기가 보는 것이다.
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': {
              'id': 9,
              'name': '김하늘 학부모',
              'role': 'PARENT',
              'phone': '01098765432',
              'mustChangePassword': false,
            },
            'error': null,
          },
        ),
      ],
    );

    final me = await repoWith(adapter).me();

    expect(me.role, UserRole.parent);
    expect(me.phone, '01098765432');
    expect(me.mustChangePassword, isFalse);
    expect(adapter.received.single.path, '/api/auth/me');
  });

  test('가입이 결과를 돌려준다', () async {
    // 웹은 이 값으로 성공 화면에 이름·반·아이디를 보여준다.
    // 반 코드에는 번호 대조가 없어서 엉뚱한 반에 가입해도 조용히 넘어가는데,
    // 반 이름을 보여주면 그 자리에서 알아차린다.
    //
    // 필드는 백엔드 SignupResponse.java(record)를 따른다 — studentId는 없고
    // role·initialPassword가 있다. (자세한 내용은 보고서 Step 1 참고.)
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': {
              'role': 'STUDENT',
              'loginId': '01012345678',
              'studentName': '김하늘',
              'classRoomName': 'A고 2학년 목요일반',
              'initialPassword': '0000',
            },
            'error': null,
          },
        ),
      ],
    );

    final res = await repoWith(adapter).signup(
      code: 'ABCD12',
      name: '김하늘',
      phone: '01012345678',
      parentPhone: '01098765432',
    );

    expect(res.role, UserRole.student);
    expect(res.studentName, '김하늘');
    expect(res.classRoomName, 'A고 2학년 목요일반');
    expect(res.loginId, '01012345678');
    expect(res.initialPassword, '0000');

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

  test('개인 코드 가입은 이름과 보호자 번호를 보내지 않는다', () async {
    // 선생님이 직접 등록한 학생이다 — 이름은 이미 서버에 있다.
    // classRoomName은 백엔드가 @JsonInclude(NON_NULL)이라 null이면 키 자체가
    // 빠진다 — 여기서도 그 모양 그대로 준다.
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': {
              'role': 'STUDENT',
              'loginId': '01055556666',
              'studentName': '박서준',
              'initialPassword': '0000',
            },
            'error': null,
          },
        ),
      ],
    );

    final res = await repoWith(adapter).signup(
      code: 'K7F2QX',
      phone: '01055556666',
    );

    expect(res.classRoomName, isNull);
    final sent = adapter.received.single.data as Map;
    expect(sent['name'], isNull);
    expect(sent['parentPhone'], isNull);
  });

  test('SignupResponse.fromJson이 classRoomName 부재를 null로 받는다', () {
    // 백엔드가 @JsonInclude(NON_NULL)이라 null일 때는 명시적 null이 아니라
    // 키 자체가 응답에서 빠진다. fromJson이 그 경우도 null로 받아야 한다.
    final res = SignupResponse.fromJson(const {
      'role': 'STUDENT',
      'loginId': '01055556666',
      'studentName': '박서준',
      'initialPassword': '0000',
    });

    expect(res.classRoomName, isNull);
    expect(res.studentName, '박서준');
    expect(res.role, UserRole.student);
  });

  test('SignupResponse.fromJson이 명시적 null도 받는다', () {
    final res = SignupResponse.fromJson(const {
      'role': 'STUDENT',
      'loginId': '01055556666',
      'studentName': '박서준',
      'classRoomName': null,
      'initialPassword': '0000',
    });

    expect(res.classRoomName, isNull);
  });

  test('비밀번호 변경과 로그아웃은 data가 null인 성공을 받는다', () async {
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
          statusCode: 200,
          body: {'success': true, 'data': null, 'error': null},
        ),
        FakeReply(
          statusCode: 200,
          body: {'success': true, 'data': null, 'error': null},
        ),
      ],
    );
    final repo = repoWith(adapter);

    await repo.changePassword(currentPassword: '0000', newPassword: 'newpass1');
    await repo.logout();

    expect(adapter.received[0].path, '/api/auth/password');
    expect(adapter.received[0].method, 'PATCH');
    expect(adapter.received[1].path, '/api/auth/logout');
  });

  test('운영 조립에서도 401이 ApiException이 된다', () async {
    // Task 6의 401 리프레시와 Task 7의 403 게이트가 DioException 자체에 기대고 있어서
    // validateStatus를 켤 수 없다. 저장소가 DioException을 잡아 ApiException으로
    // 바꿔줘야 화면이 ApiException 하나만 알면 된다. 이 테스트는 그 변환을 검증한다.
    final adapter = FakeAdapter(
      replies: const [
        FakeReply(
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

    final dio = DioClient.build(
      baseUrl: 'https://example.test',
      tokens: TokenStore(InMemoryKeyValueStore()),
      jar: CookieJar(),
    );
    dio.httpClientAdapter = adapter;
    final repo = AuthRepository(dio);

    await expectLater(
      repo.login(loginId: '01012345678', password: 'wrong'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.code,
          'code',
          'INVALID_CREDENTIALS',
        ),
      ),
    );
  });
}
