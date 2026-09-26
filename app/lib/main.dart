import 'dart:async';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'core/api/dio_client.dart';
import 'core/api/password_gate_interceptor.dart';
import 'core/api/s3_uploader.dart';
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
import 'features/parent/homeworks/parent_homework_data.dart';
import 'features/parent/homeworks/parent_homeworks_page.dart';
import 'features/parent/me/parent_me_data.dart';
import 'features/parent/me/parent_me_page.dart';
import 'features/parent/notices/parent_notices_page.dart';
import 'features/parent/report/parent_report_data.dart';
import 'features/parent/report/parent_report_page.dart';
import 'features/parent/scores/parent_score_data.dart';
import 'features/parent/scores/parent_scores_page.dart';
import 'features/parent/schedule/parent_schedule_data.dart';
import 'features/parent/schedule/parent_schedule_page.dart';
import 'features/parent/selected_child.dart';
import 'features/student/attendances/student_attendance_data.dart';
import 'features/student/attendances/student_attendance_page.dart';
import 'features/student/home/student_home_controller.dart';
import 'features/student/home/student_home_page.dart';
import 'features/student/home/student_home_repository.dart';
import 'features/student/homeworks/student_homework_controllers.dart';
import 'features/student/homeworks/student_homework_detail_page.dart';
import 'features/student/homeworks/student_homework_repository.dart';
import 'features/student/homeworks/student_homeworks_page.dart';
import 'features/student/homeworks/submission_media.dart';
import 'features/student/lessons/student_lesson_data.dart';
import 'features/student/lessons/student_lesson_detail_page.dart';
import 'features/student/lessons/student_lessons_page.dart';
import 'features/student/notices/student_notices_page.dart';
import 'features/student/scores/student_score_data.dart';
import 'features/student/scores/student_scores_page.dart';
import 'features/student/stubs/student_stubs.dart';
import 'shared/branding.dart';
import 'shared/notice/notice_board.dart';
import 'shared/notice/notice_data.dart';
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
    this.mediaPicker,
    this.openUrl,
  });

  final AuthController auth;

  /// 테스트가 가짜를 넣는다. 없으면 기기의 카메라·앨범이다.
  final MediaPicker? mediaPicker;

  /// 공지 첨부 받기. 테스트가 가짜를 넣는다. 없으면 시스템 브라우저다.
  final UrlOpener? openUrl;

  /// 기기 저장소. 학부모가 고른 자녀를 앱을 껐다 켜도 기억한다
  /// ([SelectedChild] 의 주석).
  final KeyValueStore store;

  /// 화면들의 저장소가 쓰는 Dio. 인터셉터가 붙은 것 하나뿐이다 —
  /// 화면에서 새로 만들지 마라.
  final Dio dio;

  @override
  State<AcademyApp> createState() => _AcademyAppState();
}

/// **한 사람에게 딸린 상태 전부.** 로그인하면 만들고 로그아웃하면
/// `dispose()` 한다 — 비우는(reset) 게 아니라 버린다.
///
/// 같은 폰으로 다음 사람(형제·자매, 그 부모)이 로그인했을 때 앞 사람의 홈·
/// 자녀가 보이면 안 된다. 서버의 접근 가드는 새 요청을 막을 뿐 이미 받은
/// 데이터는 못 지운다. 객체마다 reset 을 두고 로그아웃에서 하나씩 부르면
/// 넷째를 더하는 사람이 그 줄을 잊는다 — 여기 넣으면 잊을 수가 없다.
/// 로그아웃 뒤 도착한 앞 사람의 응답은 dispose 된 객체로 가서 버려진다.
///
/// **사람에게 딸린 것(B2~B4 의 화면 컨트롤러 포함)은 여기에 더하고
/// [dispose] 에 한 줄 더한다.** 라우트 빌더는 `_AcademyAppState._session` 을
/// 읽는다. 화면은 바뀐 컨트롤러를 `didUpdateWidget` 에서 갈아탄다.
///
/// 세션 안에서는 앱 수명처럼 산다 — 탭을 옮겨도 살아 있어야 컨트롤러의 60초
/// 규칙이 뜻을 갖는다. 화면이 만들면 홈에 돌아올 때마다 새로 부른다.
class _Session {
  _Session({required Dio dio, required KeyValueStore store})
    : studentHome = StudentHomeController(
        repository: StudentHomeRepository(dio),
      ),
      studentHomeworks = StudentHomeworksController(
        repository: StudentHomeworkRepository(dio),
      ),
      studentHomeworkDetail = StudentHomeworkDetailController(
        repository: StudentHomeworkRepository(dio),
      ),
      uploader = SubmissionUploader(
        repository: StudentHomeworkRepository(dio),
        s3: S3Uploader(),
      ),
      studentAttendance = StudentAttendanceController(
        repository: StudentAttendanceRepository(dio),
      ),
      studentNotices = NoticeListController(repository: NoticeRepository(dio)),
      studentLessons = StudentLessonsController(
        repository: StudentLessonRepository(dio),
      ),
      studentLessonDetail = StudentLessonDetailController(
        repository: StudentLessonRepository(dio),
      ),
      studentScores = StudentScoreController(
        repository: StudentScoreRepository(dio),
      ),
      selectedChild = SelectedChild(dio: dio, store: store),
      parentHome = ParentHomeController(repository: ParentHomeRepository(dio)),
      parentHomeworks = ParentHomeworksController(
        repository: ParentHomeworkRepository(dio),
      ),
      parentSchedule = ParentScheduleController(
        repository: ParentScheduleRepository(dio),
      ),
      parentNotices = NoticeListController(repository: NoticeRepository(dio)),
      parentMe = ParentMeController(repository: ParentMeRepository(dio)),
      parentScores = ParentScoreController(
        repository: ParentScoreRepository(dio),
      ),
      parentReport = ParentReportController(
        repository: ParentReportRepository(dio, ParentScoreRepository(dio)),
      );

  final StudentHomeController studentHome;
  final StudentHomeworksController studentHomeworks;
  final StudentHomeworkDetailController studentHomeworkDetail;

  /// 상태가 없다. 세션에 두는 이유는 저장소가 이 사람의 Dio 로 부르기 때문이다.
  final SubmissionUploader uploader;

  final StudentAttendanceController studentAttendance;
  final NoticeListController studentNotices;
  final StudentLessonsController studentLessons;
  final StudentLessonDetailController studentLessonDetail;
  final StudentScoreController studentScores;

  /// 상세에서 올림·지움·제출이 성공했다 — 목록의 개수·배지와 홈의 미완료
  /// 숙제 수가 낡았다. 지금 부르지 않고 다음에 보일 때 부른다.
  void studentHomeworkChanged() {
    studentHomeworks.markStale();
    studentHome.markStale();
  }

  /// **자녀 목록은 한 곳에서 관리한다** — 하위 화면(B2~B3)도 같은
  /// [SelectedChild] 를 받아야 자녀를 바꿨을 때 전부 같이 바뀐다.
  final SelectedChild selectedChild;
  final ParentHomeController parentHome;
  final ParentHomeworksController parentHomeworks;
  final ParentScheduleController parentSchedule;
  final NoticeListController parentNotices;
  final ParentMeController parentMe;
  final ParentScoreController parentScores;
  final ParentReportController parentReport;

  void dispose() {
    studentHome.dispose();
    studentHomeworks.dispose();
    studentHomeworkDetail.dispose();
    studentAttendance.dispose();
    studentNotices.dispose();
    studentLessons.dispose();
    studentLessonDetail.dispose();
    studentScores.dispose();
    selectedChild.dispose();
    parentHome.dispose();
    parentHomeworks.dispose();
    parentSchedule.dispose();
    parentNotices.dispose();
    parentMe.dispose();
    parentScores.dispose();
    parentReport.dispose();
  }
}

class _AcademyAppState extends State<AcademyApp> {
  _Session? _currentSession;

  /// 지금 로그인한 사람의 세션. 셸 라우트는 `ready` 에서만 그려지고, 그때는
  /// [_onAuthChanged] 가 이미 만들어 둔다. 없으면 여기서 만든다(부팅 직후
  /// 이미 로그인된 채로 뜬 경우의 방어) — 로그아웃이 오면 그대로 버려진다.
  _Session get _session =>
      _currentSession ??= _Session(dio: widget.dio, store: widget.store);

  /// 카메라·앨범. 사람에게 딸린 상태가 없어 앱 수명이다.
  late final MediaPicker _mediaPicker =
      widget.mediaPicker ?? DeviceMediaPicker();

  /// 공지 첨부를 여는 곳. 사람에게 딸린 상태가 없어 앱 수명이다.
  late final UrlOpener _openUrl = widget.openUrl ?? openExternally;

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
                builder: (_, _) =>
                    StudentHomePage(controller: _session.studentHome),
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
                    builder: (_, _) => StudentAttendancePage(
                      controller: _session.studentAttendance,
                    ),
                  ),
                  GoRoute(
                    path: 'notices',
                    builder: (_, _) => StudentNoticesPage(
                      controller: _session.studentNotices,
                      openUrl: _openUrl,
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentHomeworks,
                builder: (_, _) =>
                    StudentHomeworksPage(controller: _session.studentHomeworks),
                // 상세(S-3·S-4)는 목록의 자식이다 — 뒤로 가면 목록이고, 하단
                // 탭 바가 그대로 있다. 홈의 숙제 줄도 여기로 온다.
                routes: [
                  GoRoute(
                    path: ':homeworkId',
                    builder: (_, state) => StudentHomeworkDetailPage(
                      homeworkId: int.parse(
                        state.pathParameters['homeworkId']!,
                      ),
                      controller: _session.studentHomeworkDetail,
                      uploader: _session.uploader,
                      picker: _mediaPicker,
                      onChanged: _session.studentHomeworkChanged,
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentLessons,
                builder: (_, _) =>
                    StudentLessonsPage(controller: _session.studentLessons),
                // 상세는 목록의 자식이다 — 뒤로 가면 목록이고 탭 바가 남는다.
                // 홈의 「지난 수업」 카드도 여기로 온다.
                routes: [
                  GoRoute(
                    path: ':lessonId',
                    builder: (_, state) => StudentLessonDetailPage(
                      lessonId: int.parse(state.pathParameters['lessonId']!),
                      controller: _session.studentLessonDetail,
                      openUrl: _openUrl,
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentScores,
                builder: (_, _) => StudentScoresPage(
                  controller: _session.studentScores,
                  onLogout: widget.auth.logout,
                ),
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
                  controller: _session.parentHome,
                  selectedChild: _session.selectedChild,
                ),
                // 홈 퀵 레일이 가는 둘. **셸 안의 자식 라우트다** — 하단 탭
                // 바가 그대로 있어야 한다(학생 쪽 넷과 같은 방식).
                routes: [
                  GoRoute(
                    path: 'homeworks',
                    builder: (_, _) => ParentHomeworksPage(
                      controller: _session.parentHomeworks,
                      selectedChild: _session.selectedChild,
                    ),
                  ),
                  GoRoute(
                    path: 'notices',
                    builder: (_, _) => ParentNoticesPage(
                      controller: _session.parentNotices,
                      selectedChild: _session.selectedChild,
                      openUrl: _openUrl,
                    ),
                  ),
                ],
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentSchedule,
                builder: (_, _) => ParentSchedulePage(
                  controller: _session.parentSchedule,
                  selectedChild: _session.selectedChild,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentLessons,
                builder: (_, _) => ParentReportPage(
                  controller: _session.parentReport,
                  selectedChild: _session.selectedChild,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentScores,
                builder: (_, _) => ParentScoresPage(
                  controller: _session.parentScores,
                  selectedChild: _session.selectedChild,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.parentMe,
                builder: (_, _) => ParentMePage(
                  controller: _session.parentMe,
                  onLogout: widget.auth.logout,
                ),
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
    _onAuthChanged();
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
    _currentSession?.dispose();
    super.dispose();
  }

  /// 로그인하면 세션을 만들고, 로그아웃하면 버린다([_Session]).
  ///
  /// 버리는 신호는 `loggedOut` 하나다 — 로그아웃·세션 만료·비밀번호 변경 후
  /// 재로그인·부팅 복원 실패가 전부 [AuthController] 에서 이 상태로 간다.
  /// 여러 번 불려도 무해하다. `setState` 를 부르지 않는다 — 라우터가 같은
  /// 신호로 로그인 화면으로 옮기고, 다음 로그인 때 셸이 새로 그려지며
  /// 빌더가 새 세션을 읽는다.
  void _onAuthChanged() {
    switch (widget.auth.snapshot.status) {
      case AuthStatus.loggedOut:
        _currentSession?.dispose();
        _currentSession = null;
      case AuthStatus.ready:
        _currentSession ??= _Session(dio: widget.dio, store: widget.store);
      case AuthStatus.unknown:
      case AuthStatus.mustChangePassword:
        break;
    }
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
