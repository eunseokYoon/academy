import 'dart:async';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'core/api/dio_client.dart';
import 'core/api/password_gate_interceptor.dart';
import 'core/api/refresh_interceptor.dart';
import 'core/auth/auth_controller.dart';
import 'core/auth/auth_repository.dart';
import 'core/auth/auth_status.dart';
import 'core/router/app_router.dart';
import 'core/router/routes.dart';
import 'core/storage/cookie_store.dart';
import 'core/storage/key_value_store.dart';
import 'core/storage/token_store.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/login_page.dart';
import 'features/auth/password_change_page.dart';
import 'features/auth/privacy_page.dart';
import 'features/auth/signup_page.dart';
import 'features/auth/teacher_notice_page.dart';
import 'features/auth/terms_page.dart';
import 'features/parent/home/parent_home_controller.dart';
import 'features/parent/home/parent_home_page.dart';
import 'features/parent/home/parent_home_repository.dart';
import 'features/parent/selected_child.dart';
import 'features/parent/stubs/parent_stubs.dart';
import 'features/student/home/student_home_controller.dart';
import 'features/student/home/student_home_page.dart';
import 'features/student/home/student_home_repository.dart';
import 'features/student/stubs/student_stubs.dart';
import 'shared/branding.dart';
import 'shared/widgets/role_shell.dart';

/// 운영은 `--dart-define=API_BASE_URL=https://...`로 넣는다.
///
/// 로컬 기본값이 플랫폼마다 다르다 — **Android 에뮬레이터는 호스트를
/// `10.0.2.2`로 본다.** `localhost`를 쓰면 에뮬레이터 자기 자신을 가리켜
/// 연결이 조용히 실패한다. iOS 시뮬레이터는 호스트와 같은 `localhost`다.
String resolveBaseUrl() {
  const fromDefine = String.fromEnvironment('API_BASE_URL');
  final value = fromDefine.isNotEmpty
      ? fromDefine
      : (Platform.isAndroid ? 'http://10.0.2.2:8080' : 'http://localhost:8080');
  // 끝의 '/'를 남기면 Uri.parse('$baseUrl/api/auth')가 '//api/auth'가 되어
  // 쿠키 path가 요청 path와 영영 안 맞는다 — 리프레시 쿠키가 조용히 안 실려
  // 나가서, 액세스 토큰이 만료될 때마다 전원 재로그인하게 된다.
  return value.replaceAll(RegExp(r'/+$'), '');
}

void main() {
  final baseUrl = resolveBaseUrl();

  final secure = SecureKeyValueStore();
  final tokens = TokenStore(secure);
  final jar = CookieJar();
  final cookies = CookieStore(secure, Uri.parse('$baseUrl/api/auth'));

  // 리프레시와 재시도 전용 Dio. RefreshInterceptor가 붙지 않아야 한다 —
  // 붙으면 리프레시가 401일 때 재귀한다. 쿠키 자는 공유한다.
  // 타임아웃은 DioClient.build와 반드시 같아야 한다 — RefreshInterceptor가
  // QueuedInterceptor라 리프레시 하나가 멈추면 뒤에 줄 선 401 복구가 전부
  // 막힌다. dio의 기본값은 타임아웃이 없다(null)라서 여기서 빠뜨리면
  // 영원히 멈출 수 있다.
  final plain = Dio(
    BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: DioClient.connectTimeout,
      receiveTimeout: DioClient.receiveTimeout,
    ),
  )..interceptors.add(CookieManager(jar));

  // 순환을 끊는다. 클로저가 auth를 나중에 읽는다.
  late final AuthController auth;

  final dio = DioClient.build(
    baseUrl: baseUrl,
    tokens: tokens,
    jar: jar,
    extra: [
      RefreshInterceptor(
        plain: plain,
        tokens: tokens,
        cookies: cookies,
        jar: jar,
        onSessionExpired: () => auth.onSessionExpired(),
      ),
      PasswordGateInterceptor(() => auth.markPasswordChangeRequired()),
    ],
  );

  auth = AuthController(
    repository: AuthRepository(dio),
    tokens: tokens,
    cookies: cookies,
    jar: jar,
  );

  runApp(AcademyApp(auth: auth, dio: dio, store: secure));
  // 첫 프레임을 막지 않는다. splash가 떠 있는 동안 복원한다.
  unawaited(auth.bootstrap());
}

class AcademyApp extends StatefulWidget {
  const AcademyApp({
    super.key,
    required this.auth,
    required this.dio,
    required this.store,
  });

  final AuthController auth;

  /// 기기 저장소. 학부모가 고른 자녀를 앱을 껐다 켜도 기억한다
  /// ([SelectedChild] 의 주석).
  final KeyValueStore store;

  /// 화면들의 저장소가 쓰는 Dio. 인터셉터가 붙은 것 하나뿐이다 —
  /// 화면에서 새로 만들지 마라.
  final Dio dio;

  @override
  State<AcademyApp> createState() => _AcademyAppState();
}

class _AcademyAppState extends State<AcademyApp> {
  /// **앱이 하나만 들고 있는다.** 탭을 옮겨도 살아 있어야 컨트롤러의 60초
  /// 규칙이 뜻을 갖는다 — 화면이 만들면 홈에 돌아올 때마다 새로 부른다.
  late final StudentHomeController _studentHome = StudentHomeController(
    repository: StudentHomeRepository(widget.dio),
  );

  /// 학부모 쪽 둘. 학생 홈 컨트롤러와 같은 이유로 앱이 들고 있는다.
  /// **자녀 목록은 셸이 한 곳에서 관리한다** — 하위 화면(B2~B3)도 같은
  /// [SelectedChild] 를 받아야 자녀를 바꿨을 때 전부 같이 바뀐다.
  late final SelectedChild _selectedChild = SelectedChild(
    dio: widget.dio,
    store: widget.store,
  );
  late final ParentHomeController _parentHome = ParentHomeController(
    repository: ParentHomeRepository(widget.dio),
  );

  late final GoRouter _router = buildRouter(
    auth: widget.auth,
    routes: [
      GoRoute(
        path: AppRoutes.login,
        builder: (_, _) => LoginPage(onLogin: widget.auth.login),
      ),
      GoRoute(
        path: AppRoutes.signup,
        builder: (_, _) => SignupPage(onSignup: widget.auth.signup),
      ),
      GoRoute(
        path: AppRoutes.password,
        builder: (_, _) => PasswordChangePage(
          onChange: widget.auth.changePassword,
          onLogout: widget.auth.logout,
        ),
      ),
      GoRoute(path: AppRoutes.terms, builder: (_, _) => const TermsPage()),
      GoRoute(path: AppRoutes.privacy, builder: (_, _) => const PrivacyPage()),
      StatefulShellRoute.indexedStack(
        builder: (_, _, shell) =>
            RoleShell(role: '학생', tabs: kStudentTabs, navigationShell: shell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.student,
                builder: (_, _) => StudentHomePage(controller: _studentHome),
                // 홈 퀵 레일이 가는 넷. **셸 안의 자식 라우트다** — 하단 탭
                // 바가 그대로 있어야 한다. 경로에 앞 `/` 를 붙이지 마라,
                // go_router 가 부모와 안 맞물린다.
                routes: [
                  GoRoute(
                    path: 'clinics',
                    builder: (_, _) => const StudentClinicsStub(),
                  ),
                  GoRoute(
                    path: 'online-tests',
                    builder: (_, _) => const StudentOnlineTestsStub(),
                  ),
                  GoRoute(
                    path: 'attendances',
                    builder: (_, _) => const StudentAttendancesStub(),
                  ),
                  GoRoute(
                    path: 'notices',
                    builder: (_, _) => const StudentNoticesStub(),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentHomeworks,
                builder: (_, _) => const StudentHomeworksStub(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentLessons,
                builder: (_, _) => const StudentLessonsStub(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentScores,
                builder: (_, _) =>
                    StudentScoresStub(onLogout: widget.auth.logout),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentQna,
                builder: (_, _) => const StudentQnaStub(),
              ),
            ],
          ),
        ],
      ),
      StatefulShellRoute.indexedStack(
        builder: (_, _, shell) =>
            RoleShell(role: '학부모', tabs: kParentTabs, navigationShell: shell),
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parent,
                builder: (_, _) => ParentHomePage(
                  controller: _parentHome,
                  selectedChild: _selectedChild,
                ),
                // 홈 퀵 레일이 가는 둘. **셸 안의 자식 라우트다** — 하단 탭
                // 바가 그대로 있어야 한다(학생 쪽 넷과 같은 방식).
                routes: [
                  GoRoute(
                    path: 'homeworks',
                    builder: (_, _) => const ParentHomeworksStub(),
                  ),
                  GoRoute(
                    path: 'notices',
                    builder: (_, _) => const ParentNoticesStub(),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentSchedule,
                builder: (_, _) => const ParentScheduleStub(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentLessons,
                builder: (_, _) => const ParentLessonsStub(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentScores,
                builder: (_, _) => const ParentScoresStub(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentMe,
                builder: (_, _) => ParentMeStub(onLogout: widget.auth.logout),
              ),
            ],
          ),
        ],
      ),
      GoRoute(
        path: AppRoutes.teacherNotice,
        builder: (_, _) => TeacherNoticePage(onLogout: widget.auth.logout),
      ),
    ],
  );

  @override
  void initState() {
    super.initState();
    widget.auth.addListener(_onAuthChanged);
  }

  @override
  void didUpdateWidget(covariant AcademyApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.auth != widget.auth) {
      oldWidget.auth.removeListener(_onAuthChanged);
      widget.auth.addListener(_onAuthChanged);
    }
  }

  @override
  void dispose() {
    widget.auth.removeListener(_onAuthChanged);
    super.dispose();
  }

  /// **로그아웃하면 사람에게 딸린 상태를 전부 비운다.** 위 세 객체는 앱
  /// 수명 동안 살아 있어서, 안 비우면 같은 폰으로 다음 사람(형제·자매,
  /// 그 부모)이 로그인했을 때 앞 사람의 홈·자녀가 그대로 보인다. 서버의
  /// 접근 가드는 새 요청을 막을 뿐 이미 받은 데이터는 못 지운다.
  ///
  /// 신호는 `loggedOut` 하나다 — 로그아웃·세션 만료·비밀번호 변경 후
  /// 재로그인·부팅 복원 실패가 전부 [AuthController] 에서 이 상태로 간다.
  /// 여러 번 불려도 무해하다.
  void _onAuthChanged() {
    if (widget.auth.snapshot.status != AuthStatus.loggedOut) return;
    _studentHome.reset();
    _parentHome.reset();
    _selectedChild.reset();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: academyName,
      theme: AppTheme.light(),
      routerConfig: _router,
    );
  }
}
