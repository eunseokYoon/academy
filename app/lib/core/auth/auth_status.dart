import 'models/user_role.dart';

/// `unknown`이 있는 이유는 부팅이다. 이게 없으면 토큰을 읽는 몇십 밀리초 동안
/// 로그인 화면이 깜빡 보이고, 이미 로그인한 사용자가 매번 그것을 본다.
enum AuthStatus { unknown, loggedOut, mustChangePassword, ready }

class AuthSnapshot {
  const AuthSnapshot({required this.status, this.role, this.name});

  const AuthSnapshot.unknown() : status = AuthStatus.unknown, role = null, name = null;
  const AuthSnapshot.loggedOut() : status = AuthStatus.loggedOut, role = null, name = null;

  final AuthStatus status;

  /// `mustChangePassword` 상태에서도 채워져 있다 — 비밀번호를 바꾸면 그 역할
  /// 화면으로 돌아가야 한다.
  final UserRole? role;
  final String? name;
}
