import 'package:cookie_jar/cookie_jar.dart';
import 'package:flutter/foundation.dart';

import '../api/api_exception.dart';
import '../storage/cookie_store.dart';
import '../storage/token_store.dart';
import 'auth_repository.dart';
import 'auth_status.dart';
import 'models/signup_response.dart';

/// 로그인 상태의 정본. 라우터가 이것을 듣고 화면을 고른다(Task 10).
class AuthController extends ChangeNotifier {
  AuthController({
    required this._repository,
    required this._tokens,
    required this._cookies,
    required this._jar,
    this._beforeLogout,
  });

  final AuthRepository _repository;
  final TokenStore _tokens;
  final CookieStore _cookies;
  final CookieJar _jar;

  /// 로그아웃 직전, 아직 인증이 살아 있을 때 할 일. 푸시 기기 토큰 해제
  /// (`PushRegistrar.unregister`)가 여기 붙는다 — 서버 로그아웃 뒤에는 못 부른다.
  final Future<void> Function()? _beforeLogout;
  static const _beforeLogoutTimeout = Duration(seconds: 8);

  AuthSnapshot _snapshot = const AuthSnapshot.unknown();
  AuthSnapshot get snapshot => _snapshot;

  /// 부팅 복원이 **연결 문제로** 실패했다. 토큰은 남겨 두고 상태는 `unknown` 그대로라
  /// 스플래시가 이 문구와 「다시 시도」를 띄운다([bootstrap] 을 다시 부른다).
  String? _bootError;
  String? get bootError => _bootError;

  /// 앱이 뜰 때 한 번 부른다.
  ///
  /// **쿠키를 jar에 심는 것이 먼저다.** 순서가 어긋나면 첫 요청의 401 리프레시가
  /// 쿠키 없이 나가서 실패하고, 앱을 다시 열 때마다 재로그인을 하게 된다.
  Future<void> bootstrap() async {
    if (_bootError != null) {
      _bootError = null; // 다시 시도 — 스플래시를 로더로 되돌린다
      notifyListeners();
    }
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
    } on ApiException catch (e) {
      final status = e.statusCode;
      if (status != null && status >= 400 && status < 500) {
        // 서버가 거절했다 — 토큰이 죽었고 리프레시도 못 살렸다. 남겨두면 매 요청이 401이다.
        await _clearLocal();
        _set(const AuthSnapshot.loggedOut());
        return;
      }
      _failBoot();
    } catch (_) {
      _failBoot();
    }
  }

  /// 연결 실패·5xx. **로그인 정보를 지우지 않는다**(2026-09-30 리뷰) — 예전에는 어떤 실패든
  /// 비워서 망이 없을 때 앱을 열면 매번 로그아웃됐고, 푸시 기기 토큰까지 지워졌다.
  void _failBoot() {
    // 리프레시 인터셉터가 이미 세션 만료로 처리했으면(loggedOut) 그대로 둔다
    if (_snapshot.status == AuthStatus.loggedOut) return;
    _bootError = '연결할 수 없습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.';
    notifyListeners();
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
      await _beforeLogout?.call().timeout(_beforeLogoutTimeout);
    } catch (_) {
      // 알림 해제는 곁다리다. 실패해도 로그아웃은 한다.
    }
    try {
      await _repository.logout();
    } catch (_) {
      // 무시한다. 아래에서 로컬을 비우는 것이 본질이다.
    }
    await _clearLocal();
    _set(const AuthSnapshot.loggedOut());
  }

  /// 본인 계정 삭제(2026-10-03). 서버가 로그인 계정만 지우고 학습 기록은 남긴다
  /// (`AccountDeletionService`). 실패하면(비밀번호가 틀림 등) 던진다 — 화면이 문구를 띄운다.
  ///
  /// 성공하면 [logout] 과 같은 끝으로 간다. `loggedOut` 이 되면 `PushRegistrar` 가 기기 토큰을
  /// 지우고 세션이 버려진다. 서버의 기기 토큰은 계정과 함께 CASCADE 로 지워져서
  /// `beforeLogout`(기기 해제 호출)은 부르지 않는다 — 계정이 없어진 뒤라 401 이다.
  Future<void> deleteAccount(String password) async {
    await _repository.deleteAccount(password);
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
  ///
  /// 결과를 그대로 돌려준다. 화면이 반 이름을 보여줘야 한다.
  Future<SignupResponse> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
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
