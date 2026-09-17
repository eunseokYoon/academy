import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/auth_status.dart';
import 'package:academy_app/core/auth/models/login_response.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

class _FakeRepo implements AuthRepository {
  _FakeRepo({this.meResult, this.loginResult, this.throwOnMe = false});

  MeResponse? meResult;
  LoginResponse? loginResult;
  bool throwOnMe;

  int meCalls = 0;
  int logoutCalls = 0;
  int changeCalls = 0;
  int signupCalls = 0;

  @override
  Future<MeResponse> me() async {
    meCalls++;
    if (throwOnMe) {
      throw const ApiException(code: 'TOKEN_INVALID', message: '유효하지 않은 인증 정보입니다.');
    }
    return meResult!;
  }

  @override
  Future<LoginResponse> login({
    required String loginId,
    required String password,
  }) async =>
      loginResult!;

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async =>
      changeCalls++;

  @override
  Future<void> logout() async => logoutCalls++;

  @override
  Future<void> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) async {
    signupCalls++;
  }
}

// AuthRepository의 공개 메서드 다섯 개를 전부 구현했으므로 noSuchMethod가 필요 없다.
// 백엔드에 엔드포인트가 늘면 이 클래스가 컴파일 에러로 알려준다 — 그게 맞는 방향이다.

void main() {
  late TokenStore tokens;
  late CookieStore cookies;
  late KeyValueStore cookieKv;
  late CookieJar jar;

  setUp(() {
    tokens = TokenStore(InMemoryKeyValueStore());
    cookieKv = InMemoryKeyValueStore();
    cookies = CookieStore(cookieKv, Uri.parse('https://example.test/api/auth'));
    jar = CookieJar();
  });

  AuthController controllerWith(AuthRepository repo) => AuthController(
        repository: repo,
        tokens: tokens,
        cookies: cookies,
        jar: jar,
      );

  MeResponse meOf(UserRole role, {bool mustChange = false}) => MeResponse(
        id: 1,
        name: '김하늘',
        role: role,
        phone: '01012345678',
        mustChangePassword: mustChange,
      );

  test('초기 상태는 unknown이다', () {
    // 부팅 중에 로그인 화면을 깜빡 보여주지 않으려면 셋째 상태가 필요하다.
    expect(controllerWith(_FakeRepo()).snapshot.status, AuthStatus.unknown);
  });

  test('토큰이 없으면 부팅 후 loggedOut이다', () async {
    final repo = _FakeRepo();
    final c = controllerWith(repo);

    await c.bootstrap();

    expect(c.snapshot.status, AuthStatus.loggedOut);
    // 토큰이 없으면 me 를 부를 이유가 없다
    expect(repo.meCalls, 0);
  });

  test('토큰이 있으면 me로 역할을 복원한다', () async {
    await tokens.write('at-1');
    final repo = _FakeRepo(meResult: meOf(UserRole.parent));
    final c = controllerWith(repo);

    await c.bootstrap();

    expect(c.snapshot.status, AuthStatus.ready);
    expect(c.snapshot.role, UserRole.parent);
    expect(c.snapshot.name, '김하늘');
    expect(repo.meCalls, 1);
  });

  test('부팅이 리프레시 쿠키를 jar에 먼저 심는다', () async {
    // 이 순서가 어긋나면 첫 요청의 401 리프레시가 쿠키 없이 나가서 실패하고,
    // 앱을 다시 열 때마다 재로그인을 하게 된다.
    await cookieKv.write('refresh_cookie', 'rt-1');
    await tokens.write('at-1');
    final c = controllerWith(_FakeRepo(meResult: meOf(UserRole.student)));

    await c.bootstrap();

    final loaded = await jar.loadForRequest(Uri.parse('https://example.test/api/auth'));
    expect(loaded.map((e) => e.name), contains('refreshToken'));
  });

  test('me가 실패하면 보관소를 비우고 loggedOut이다', () async {
    await tokens.write('at-1');
    await cookieKv.write('refresh_cookie', 'rt-1');
    final c = controllerWith(_FakeRepo(throwOnMe: true));

    await c.bootstrap();

    expect(c.snapshot.status, AuthStatus.loggedOut);
    expect(await tokens.read(), isNull);
    expect(await cookieKv.read('refresh_cookie'), isNull);
  });

  test('mustChangePassword면 ready가 아니다', () async {
    await tokens.write('at-1');
    final c = controllerWith(_FakeRepo(meResult: meOf(UserRole.student, mustChange: true)));

    await c.bootstrap();

    expect(c.snapshot.status, AuthStatus.mustChangePassword);
  });

  test('로그인이 토큰을 저장하고 상태를 바꾼다', () async {
    final repo = _FakeRepo(
      loginResult: const LoginResponse(
        accessToken: 'at-new',
        user: UserSummary(
          id: 1,
          name: '김하늘',
          role: UserRole.student,
          mustChangePassword: false,
        ),
      ),
    );
    final c = controllerWith(repo);
    var notified = 0;
    c.addListener(() => notified++);

    await c.login(loginId: '01012345678', password: '0000');

    expect(await tokens.read(), 'at-new');
    expect(c.snapshot.status, AuthStatus.ready);
    expect(c.snapshot.role, UserRole.student);
    expect(notified, greaterThan(0));
  });

  test('초기 비밀번호로 로그인하면 mustChangePassword다', () async {
    final repo = _FakeRepo(
      loginResult: const LoginResponse(
        accessToken: 'at-new',
        user: UserSummary(
          id: 1,
          name: '김하늘',
          role: UserRole.student,
          mustChangePassword: true,
        ),
      ),
    );
    final c = controllerWith(repo);

    await c.login(loginId: '01012345678', password: '0000');

    expect(c.snapshot.status, AuthStatus.mustChangePassword);
    // 토큰은 저장해야 한다 — 비밀번호 변경 호출에 그 토큰이 필요하다
    expect(await tokens.read(), 'at-new');
  });

  test('비밀번호 변경 후에는 다시 로그인시킨다', () async {
    // 서버가 리프레시 토큰을 전부 폐기하고 쿠키도 만료시킨다.
    // 안 비우면 죽은 쿠키로 리프레시를 계속 시도한다.
    await tokens.write('at-1');
    await cookieKv.write('refresh_cookie', 'rt-1');
    final repo = _FakeRepo();
    final c = controllerWith(repo);

    await c.changePassword(currentPassword: '0000', newPassword: 'newpass1');

    expect(repo.changeCalls, 1);
    expect(c.snapshot.status, AuthStatus.loggedOut);
    expect(await tokens.read(), isNull);
    expect(await cookieKv.read('refresh_cookie'), isNull);
  });

  test('로그아웃은 서버 호출이 실패해도 로컬을 비운다', () async {
    // 비행기 모드에서 로그아웃을 눌러도 기기에 토큰이 남으면 안 된다.
    await tokens.write('at-1');
    final repo = _FakeThrowingLogout();
    final c = AuthController(
      repository: repo,
      tokens: tokens,
      cookies: cookies,
      jar: jar,
    );

    await c.logout();

    expect(c.snapshot.status, AuthStatus.loggedOut);
    expect(await tokens.read(), isNull);
  });

  test('markPasswordChangeRequired가 상태를 바꾼다', () async {
    await tokens.write('at-1');
    final c = controllerWith(_FakeRepo(meResult: meOf(UserRole.student)));
    await c.bootstrap();
    expect(c.snapshot.status, AuthStatus.ready);

    c.markPasswordChangeRequired();

    expect(c.snapshot.status, AuthStatus.mustChangePassword);
    // 역할은 유지된다 — 비밀번호를 바꾸면 그 역할 화면으로 돌아가야 한다
    expect(c.snapshot.role, UserRole.student);
  });

  test('가입은 저장소에 위임하고 상태를 바꾸지 않는다', () async {
    // 가입은 로그인이 아니다. 계정만 만들어지고 초기 비밀번호 0000으로
    // 로그인해야 한다. 여기서 상태가 바뀌면 가입 직후 화면이 역할 셸로 튄다.
    final repo = _FakeRepo();
    final c = controllerWith(repo);
    await c.bootstrap();
    expect(c.snapshot.status, AuthStatus.loggedOut);

    await c.signup(
      code: 'ABCD12',
      name: '김하늘',
      phone: '01012345678',
      parentPhone: '01098765432',
    );

    expect(c.snapshot.status, AuthStatus.loggedOut);
    expect(repo.signupCalls, 1);
  });
}

class _FakeThrowingLogout extends _FakeRepo {
  @override
  Future<void> logout() async {
    throw const ApiException(code: 'INTERNAL_ERROR', message: '서버 오류가 발생했습니다.');
  }
}
