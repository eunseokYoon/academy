import 'dart:convert';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/login_response.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/push/push_registrar.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';

import 'fake_push.dart';

/// 서버에 간 것을 순서대로 적는다. 인증 서버 호출도 같은 줄에 적어 순서를 본다.
final _log = <String>[];

class _AuthRepo implements AuthRepository {
  _AuthRepo(this.role, {this.mustChange = false});

  UserRole role;
  bool mustChange;

  UserSummary get _user => UserSummary(
    id: 1,
    name: '김하늘',
    role: role,
    mustChangePassword: mustChange,
  );

  @override
  Future<LoginResponse> login({
    required String loginId,
    required String password,
  }) async => LoginResponse(accessToken: 'at', user: _user);

  @override
  Future<MeResponse> me() async => MeResponse(
    id: 1,
    name: '김하늘',
    role: role,
    phone: '01011112222',
    mustChangePassword: mustChange,
  );

  @override
  Future<void> logout() async => _log.add('POST /api/auth/logout');

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {}

  @override
  Future<SignupResponse> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) => throw UnimplementedError();
}

class _Adapter implements HttpClientAdapter {
  int status = 200;
  final bodies = <String, Object?>{};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final key = '${options.method} ${options.path}';
    _log.add(key);
    bodies[key] = options.data;
    return ResponseBody.fromString(
      jsonEncode({'success': status == 200, 'data': null, 'error': null}),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class _Harness {
  _Harness(UserRole role, {bool mustChange = false})
    : repo = _AuthRepo(role, mustChange: mustChange) {
    final kv = InMemoryKeyValueStore();
    late final PushRegistrar r;
    auth = AuthController(
      repository: repo,
      tokens: TokenStore(kv),
      cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
      jar: CookieJar(),
      beforeLogout: () => r.unregister(),
    );
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = adapter;
    r = PushRegistrar(dio: dio, messaging: messaging, auth: auth)..start();
    registrar = r;
  }

  final _AuthRepo repo;
  final adapter = _Adapter();
  final messaging = FakePushMessaging();
  late final AuthController auth;
  late final PushRegistrar registrar;

  Future<void> login() async {
    await auth.login(loginId: '01011112222', password: 'pw');
    await registrar.idle;
  }
}

void main() {
  setUp(_log.clear);

  test('학생이 로그인하면 권한을 묻고 토큰을 올린다', () async {
    final h = _Harness(UserRole.student);
    await h.login();

    expect(h.messaging.permissionAsks, 1);
    expect(_log, ['PUT /api/push/devices']);
    expect(h.adapter.bodies['PUT /api/push/devices'], {
      'token': 'token-1',
      'platform': 'ANDROID',
    });
  });

  test('학부모도 올린다', () async {
    final h = _Harness(UserRole.parent);
    await h.login();
    expect(_log, ['PUT /api/push/devices']);
  });

  test('선생님은 올리지 않는다(15-4)', () async {
    final h = _Harness(UserRole.teacher);
    await h.login();
    expect(_log, isEmpty);
    expect(h.messaging.permissionAsks, 0);
  });

  test('비밀번호를 바꿔야 하는 동안은 올리지 않는다 — 서버가 403 이다', () async {
    final h = _Harness(UserRole.student, mustChange: true);
    await h.login();
    expect(_log, isEmpty);
  });

  test('로그아웃은 서버 로그아웃 전에 토큰을 지우고, 기기 토큰도 버린다', () async {
    final h = _Harness(UserRole.student);
    await h.login();
    _log.clear();

    await h.auth.logout();
    await h.registrar.idle;

    // 순서가 핵심이다 — 서버 로그아웃 뒤에는 인증이 없어 DELETE 를 못 친다.
    expect(_log, ['DELETE /api/push/devices', 'POST /api/auth/logout']);
    expect(h.adapter.bodies['DELETE /api/push/devices'], {'token': 'token-1'});
    expect(h.messaging.deletes, 1);
  });

  test('토큰 해제가 실패해도 로그아웃은 된다', () async {
    final h = _Harness(UserRole.student);
    await h.login();
    h.adapter.status = 500;

    await h.auth.logout();
    await h.registrar.idle;

    expect(_log.last, 'POST /api/auth/logout');
    expect(h.messaging.deletes, 1);
  });

  test('세션이 만료되면 서버에는 못 지우지만 기기 토큰은 버린다', () async {
    // 안 버리면 로그아웃된 폰이 앞 사람의 알림을 계속 받는다.
    final h = _Harness(UserRole.student);
    await h.login();
    _log.clear();

    await h.auth.onSessionExpired();
    await h.registrar.idle;

    expect(_log, isEmpty);
    expect(h.messaging.deletes, 1);
  });

  test('토큰이 바뀌면 다시 올린다. 로그아웃 뒤에는 안 올린다', () async {
    final h = _Harness(UserRole.student);
    await h.login();
    _log.clear();

    h.messaging.refreshes.add('token-2');
    await Future<void>.delayed(Duration.zero);
    await h.registrar.idle;
    expect(_log, ['PUT /api/push/devices']);
    expect(h.adapter.bodies['PUT /api/push/devices'], {
      'token': 'token-2',
      'platform': 'ANDROID',
    });

    // 새 토큰으로 지운다.
    _log.clear();
    await h.auth.logout();
    await h.registrar.idle;
    expect(h.adapter.bodies['DELETE /api/push/devices'], {'token': 'token-2'});

    _log.clear();
    h.messaging.refreshes.add('token-3');
    await Future<void>.delayed(Duration.zero);
    await h.registrar.idle;
    expect(_log, isEmpty);
  });

  test('토큰을 아직 못 받았으면(iOS APNs 전) 올리지 않고 새 토큰을 기다린다', () async {
    final h = _Harness(UserRole.student);
    h.messaging.nextToken = null;
    await h.login();
    expect(_log, isEmpty);

    h.messaging.refreshes.add('late-token');
    await Future<void>.delayed(Duration.zero);
    await h.registrar.idle;
    expect(h.adapter.bodies['PUT /api/push/devices'], {
      'token': 'late-token',
      'platform': 'ANDROID',
    });
  });

  test('다음 사람이 로그인하면 새 토큰을 받아 그 사람 몫으로 올린다', () async {
    final h = _Harness(UserRole.student);
    await h.login();
    await h.auth.logout();
    await h.registrar.idle;
    _log.clear();

    h.repo.role = UserRole.parent;
    h.messaging.nextToken = 'token-parent';
    await h.login();
    expect(_log, ['PUT /api/push/devices']);
    expect(h.adapter.bodies['PUT /api/push/devices'], {
      'token': 'token-parent',
      'platform': 'ANDROID',
    });
  });
}
