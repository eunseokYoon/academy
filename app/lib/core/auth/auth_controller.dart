import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter/foundation.dart';

import '../storage/cookie_store.dart';
import '../storage/token_store.dart';
import 'auth_repository.dart';
import 'auth_status.dart';

/// 로그인 상태의 정본. 라우터가 이것을 듣고 화면을 고른다(Task 10).
class AuthController extends ChangeNotifier {
  AuthController({
    required AuthRepository repository,
    required TokenStore tokens,
    required CookieStore cookies,
    required CookieJar jar,
  }) : // named 매개변수에 private 이름(`this._repository`)을 쓰는 것을 Dart가
       // 금지한다(컴파일 에러다, 조용히 깨지는 게 아니다). 필드를 private으로
       // 두려면 이 방식뿐이다. 아래 세 줄도 동일.
       // ignore: prefer_initializing_formals
       _repository = repository,
       // ignore: prefer_initializing_formals
       _tokens = tokens,
       // ignore: prefer_initializing_formals
       _cookies = cookies,
       // ignore: prefer_initializing_formals
       _jar = jar;

  final AuthRepository _repository;
  final TokenStore _tokens;
  final CookieStore _cookies;
  final CookieJar _jar;

  AuthSnapshot _snapshot = const AuthSnapshot.unknown();
  AuthSnapshot get snapshot => _snapshot;

  /// 앱이 뜰 때 한 번 부른다.
  ///
  /// **쿠키를 jar에 심는 것이 먼저다.** 순서가 어긋나면 첫 요청의 401 리프레시가
  /// 쿠키 없이 나가서 실패하고, 앱을 다시 열 때마다 재로그인을 하게 된다.
  Future<void> bootstrap() async {
    await _cookies.restore(_jar);

    final token = await _tokens.read();
    if (token == null) {
      _set(const AuthSnapshot.loggedOut());
      return;
    }

    try {
      final me = await _repository.me();
      _set(
        AuthSnapshot(
          status: me.mustChangePassword
              ? AuthStatus.mustChangePassword
              : AuthStatus.ready,
          role: me.role,
          name: me.name,
        ),
      );
    } catch (_) {
      // 토큰이 죽었고 리프레시도 못 살렸다. 남겨두면 매 요청이 401이다.
      await _clearLocal();
      _set(const AuthSnapshot.loggedOut());
    }
  }

  Future<void> login({
    required String loginId,
    required String password,
  }) async {
    final res = await _repository.login(loginId: loginId, password: password);
    await _tokens.write(res.accessToken);
    // 로그인 응답의 Set-Cookie 를 남긴다. 이게 리프레시 토큰이다.
    await _cookies.persist(_jar);
    _set(
      AuthSnapshot(
        status: res.user.mustChangePassword
            ? AuthStatus.mustChangePassword
            : AuthStatus.ready,
        role: res.user.role,
        name: res.user.name,
      ),
    );
  }

  /// 성공하면 서버가 리프레시 토큰을 전부 폐기한다. **다시 로그인시킨다.**
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _repository.changePassword(
      currentPassword: currentPassword,
      newPassword: newPassword,
    );
    await _clearLocal();
    _set(const AuthSnapshot.loggedOut());
  }

  /// 서버 호출이 실패해도 로컬은 비운다. 비행기 모드에서 로그아웃을 눌러도
  /// 기기에 토큰이 남으면 안 된다.
  Future<void> logout() async {
    try {
      await _repository.logout();
    } catch (_) {
      // 무시한다. 아래에서 로컬을 비우는 것이 본질이다.
    }
    await _clearLocal();
    _set(const AuthSnapshot.loggedOut());
  }

  /// `PasswordGateInterceptor`가 부른다(Task 7). 역할은 유지한다 —
  /// 비밀번호를 바꾸면 그 역할 화면으로 돌아가야 한다.
  void markPasswordChangeRequired() {
    if (_snapshot.status == AuthStatus.mustChangePassword) return;
    _set(
      AuthSnapshot(
        status: AuthStatus.mustChangePassword,
        role: _snapshot.role,
        name: _snapshot.name,
      ),
    );
  }

  /// 가입은 로그인이 아니다. 계정만 만들어지고 상태는 `loggedOut` 그대로다 —
  /// 초기 비밀번호 `0000`으로 로그인해야 한다. 그래서 `_set`을 부르지 않는다.
  Future<void> signup({
    required String code,
    required String name,
    required String phone,
    required String parentPhone,
  }) => _repository.signup(
    code: code,
    name: name,
    phone: phone,
    parentPhone: parentPhone,
  );

  /// `RefreshInterceptor`가 부른다(Task 6). 리프레시가 죽었을 때다.
  Future<void> onSessionExpired() async {
    await _clearLocal();
    _set(const AuthSnapshot.loggedOut());
  }

  Future<void> _clearLocal() async {
    await _tokens.clear();
    await _cookies.clear();
    await _jar.deleteAll();
  }

  void _set(AuthSnapshot next) {
    _snapshot = next;
    notifyListeners();
  }
}
