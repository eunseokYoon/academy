import '../auth/auth_status.dart';
import '../auth/models/user_role.dart';

class AppRoutes {
  const AppRoutes._();

  static const splash = '/splash';
  static const login = '/login';
  static const signup = '/signup';
  static const password = '/password';
  static const student = '/student';
  static const parent = '/parent';

  /// 선생님은 앱 대상이 아니다. 로그인은 성공하므로 안내 화면으로 받는다.
  static const teacherNotice = '/teacher-notice';
}

/// 인증 상태와 지금 위치로 갈 곳을 정한다. `null`이면 그대로 둔다.
///
/// **순수 함수다.** go_router의 redirect에 인라인으로 쓰지 마라 — 분기가
/// 여섯 갈래라 위젯 트리 없이 검증할 수 있어야 한다.
///
/// **같은 곳으로 보내면 안 된다.** go_router가 리다이렉트 루프로 터진다.
/// 모든 분기가 「이미 거기면 null」을 먼저 본다.
String? redirectFor(AuthSnapshot snapshot, String location) {
  switch (snapshot.status) {
    case AuthStatus.unknown:
      return location == AppRoutes.splash ? null : AppRoutes.splash;

    case AuthStatus.loggedOut:
      // 가입은 비로그인 상태에서 들어가는 화면이다
      if (location == AppRoutes.login || location == AppRoutes.signup) {
        return null;
      }
      return AppRoutes.login;

    case AuthStatus.mustChangePassword:
      return location == AppRoutes.password ? null : AppRoutes.password;

    case AuthStatus.ready:
      final home = switch (snapshot.role!) {
        UserRole.student => AppRoutes.student,
        UserRole.parent => AppRoutes.parent,
        UserRole.teacher => AppRoutes.teacherNotice,
      };
      // 자기 영역 밖(인증 화면이거나 남의 역할 영역)이면 홈으로
      return location.startsWith(home) ? null : home;
  }
}
