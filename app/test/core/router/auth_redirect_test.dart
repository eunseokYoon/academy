import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/auth_status.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/router/auth_redirect.dart';

void main() {
  const unknown = AuthSnapshot.unknown();
  const loggedOut = AuthSnapshot.loggedOut();
  const mustChange = AuthSnapshot(
    status: AuthStatus.mustChangePassword,
    role: UserRole.student,
    name: '김하늘',
  );
  const student = AuthSnapshot(
    status: AuthStatus.ready,
    role: UserRole.student,
    name: '김하늘',
  );
  const parent = AuthSnapshot(
    status: AuthStatus.ready,
    role: UserRole.parent,
    name: '김하늘 학부모',
  );
  const teacher = AuthSnapshot(
    status: AuthStatus.ready,
    role: UserRole.teacher,
    name: '선생님',
  );

  group('unknown — 부팅 중', () {
    test('어디서 시작해도 splash로 보낸다', () {
      expect(redirectFor(unknown, '/student'), AppRoutes.splash);
      expect(redirectFor(unknown, '/login'), AppRoutes.splash);
    });

    test('이미 splash면 그대로 둔다', () {
      // 같은 곳으로 계속 보내면 go_router가 리다이렉트 루프로 터진다
      expect(redirectFor(unknown, AppRoutes.splash), isNull);
    });
  });

  group('loggedOut', () {
    test('로그인으로 보낸다', () {
      expect(redirectFor(loggedOut, '/student'), AppRoutes.login);
      expect(redirectFor(loggedOut, AppRoutes.splash), AppRoutes.login);
    });

    test('로그인과 가입은 그대로 둔다', () {
      // 가입은 비로그인 상태에서 들어가는 화면이다. 막으면 신입생이 못 들어온다.
      expect(redirectFor(loggedOut, AppRoutes.login), isNull);
      expect(redirectFor(loggedOut, AppRoutes.signup), isNull);
    });
  });

  group('mustChangePassword', () {
    test('비밀번호 화면으로 보낸다', () {
      // 초기 비밀번호가 전원 0000 이다. 이게 없으면 남의 성적이 샌다.
      expect(redirectFor(mustChange, '/student'), AppRoutes.password);
      expect(redirectFor(mustChange, AppRoutes.login), AppRoutes.password);
    });

    test('이미 비밀번호 화면이면 그대로 둔다', () {
      expect(redirectFor(mustChange, AppRoutes.password), isNull);
    });
  });

  group('ready', () {
    test('역할별 홈으로 보낸다', () {
      expect(redirectFor(student, AppRoutes.login), AppRoutes.student);
      expect(redirectFor(parent, AppRoutes.login), AppRoutes.parent);
      expect(redirectFor(teacher, AppRoutes.login), AppRoutes.teacherNotice);
    });

    test('자기 영역 안이면 그대로 둔다', () {
      expect(redirectFor(student, '/student'), isNull);
      expect(redirectFor(student, '/student/homeworks'), isNull);
      expect(redirectFor(parent, '/parent/scores'), isNull);
      expect(redirectFor(parent, '/parent'), isNull);
    });

    test('남의 역할 영역이면 자기 홈으로 보낸다', () {
      // 백엔드가 경로 접두사로 403 ROLE_NOT_ALLOWED 를 준다. 화면에서도 막아야
      // 학부모가 학생 화면을 열어 빈 에러를 보지 않는다.
      expect(redirectFor(student, '/parent'), AppRoutes.student);
      expect(redirectFor(parent, '/student/homeworks'), AppRoutes.parent);
    });

    test('이름만 겹치는 라우트는 자기 영역이 아니다', () {
      // startsWith(home) 하나로 판정하면 이것이 null이 되어 리다이렉트가 사라진다.
      expect(redirectFor(student, '/students-archive'), AppRoutes.student);
      expect(redirectFor(parent, '/parent-info'), AppRoutes.parent);
    });

    test('비밀번호를 바꾼 뒤 splash에 남아 있으면 홈으로 보낸다', () {
      expect(redirectFor(student, AppRoutes.splash), AppRoutes.student);
      expect(redirectFor(student, AppRoutes.password), AppRoutes.student);
    });

    test('선생님은 안내 화면에 머문다', () {
      // 선생님은 앱 대상이 아니다. 로그인은 성공하므로 안내로 받는다.
      expect(redirectFor(teacher, AppRoutes.teacherNotice), isNull);
      expect(redirectFor(teacher, '/student'), AppRoutes.teacherNotice);
    });
  });

  group('약관·처리방침', () {
    test('라우트 상수가 웹 경로와 같다', () {
      expect(AppRoutes.terms, '/terms');
      expect(AppRoutes.privacy, '/privacy');
    });

    test('로그인 전에도 들어갈 수 있다', () {
      // 법정 고지다. 로그인해야 읽을 수 있으면 고지가 아니다.
      expect(redirectFor(loggedOut, AppRoutes.terms), isNull);
      expect(redirectFor(loggedOut, AppRoutes.privacy), isNull);
    });

    test('로그인한 뒤에는 자기 홈으로 돌려보낸다', () {
      // 읽기는 로그인 전 경로다. 로그인 상태에서 머무를 화면이 아니다.
      expect(redirectFor(student, AppRoutes.terms), AppRoutes.student);
      expect(redirectFor(parent, AppRoutes.privacy), AppRoutes.parent);
    });
  });
}
