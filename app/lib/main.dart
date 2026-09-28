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
import 'core/auth/models/user_role.dart';
import 'core/push/push_link.dart';
import 'core/push/push_messaging.dart';
import 'core/push/push_registrar.dart';
import 'core/push/push_setting.dart';
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
import 'features/student/online_tests/online_test_data.dart';
import 'features/student/online_tests/student_online_test_page.dart';
import 'features/student/online_tests/student_online_tests_page.dart';
import 'features/student/qna/qna_data.dart';
import 'features/student/qna/student_qna_detail_page.dart';
import 'features/student/qna/student_qna_page.dart';
import 'features/student/schedule/student_schedule_data.dart';
import 'features/student/schedule/student_schedule_page.dart';
import 'features/student/scores/student_score_data.dart';
import 'features/student/scores/student_scores_page.dart';
import 'shared/branding.dart';
import 'shared/notice/notice_board.dart';
import 'shared/notice/notice_data.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import 'shared/widgets/reappear_reload.dart';
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

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final baseUrl = resolveBaseUrl();
  // 설정 파일이 없는 빌드면 알림 없이 뜬다(FirebasePushMessaging.create).
  final push = await FirebasePushMessaging.create();

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

  // 순환을 끊는다. 클로저가 auth·registrar 를 나중에 읽는다.
  late final AuthController auth;
  late final PushRegistrar registrar;

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
    beforeLogout: () => registrar.unregister(),
  );
  registrar = PushRegistrar(dio: dio, messaging: push, auth: auth)..start();

  runApp(AcademyApp(auth: auth, dio: dio, store: secure, push: push));
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
    this.push = const DisabledPushMessaging(),
  });

  final AuthController auth;

  /// 테스트가 가짜를 넣는다. 없으면 기기의 카메라·앨범이다.
  final MediaPicker? mediaPicker;

  /// 공지 첨부 받기. 테스트가 가짜를 넣는다. 없으면 시스템 브라우저다.
  final UrlOpener? openUrl;

  /// 기기 저장소. 학부모가 고른 자녀를 앱을 껐다 켜도 기억한다
  /// ([SelectedChild] 의 주석).
  final KeyValueStore store;

  /// 알림을 눌렀을 때·앱이 떠 있을 때 온 알림. 토큰 등록은 `main()` 의
  /// [PushRegistrar] 가 한다 — 여기는 딥링크만 받는다. 테스트는 가짜를 넣는다.
  final PushMessaging push;

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
      studentSchedule = StudentScheduleController(
        repository: StudentScheduleRepository(dio),
      ),
      studentQna = QnaBoardController(repository: QnaRepository(dio)),
      studentQnaDetail = QnaDetailController(repository: QnaRepository(dio)),
      studentOnlineTests = OnlineTestListController(
        repository: OnlineTestRepository(dio),
      ),
      studentOnlineTest = OnlineTestTakeController(
        repository: OnlineTestRepository(dio),
      ),
      qnaUploader = QnaUploader(
        repository: QnaRepository(dio),
        s3: S3Uploader(),
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
      ),
      pushSetting = PushSettingController(
        repository: PushSettingRepository(dio),
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
  final StudentScheduleController studentSchedule;
  final QnaBoardController studentQna;
  final QnaDetailController studentQnaDetail;
  final OnlineTestListController studentOnlineTests;
  final OnlineTestTakeController studentOnlineTest;

  /// 온라인 테스트를 냈다 — 목록의 상태가 바뀌었고, 그 주차 클리닉 칸이
  /// 자동으로 채워졌을 수 있다(성적 화면).
  void studentOnlineTestSubmitted() {
    studentOnlineTests.markStale();
    studentScores.markStale();
  }

  /// 상태가 없다. 세션에 두는 이유는 저장소가 이 사람의 Dio 로 부르기 때문이다.
  final QnaUploader qnaUploader;

  /// 클리닉을 옮겼다 — 공지가 한 건 발행됐고(10-1) 홈의 「다음 클리닉」과
  /// 출석 캘린더의 클리닉 칸이 낡았다.
  void studentClinicChanged() {
    studentNotices.markStale();
    studentHome.markStale();
    studentAttendance.markStale();
  }

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

  /// 알림 받기 스위치. 학생·학부모 내 정보 두 화면이 같이 쓴다.
  final PushSettingController pushSetting;

  /// 화면 컨트롤러 전부. **새 컨트롤러는 여기에도 넣어라** — 알림이 왔을 때
  /// [markAllStale] 이 이 목록만 본다.
  late final List<ParamController<dynamic, dynamic>> _screens = [
    studentHome,
    studentHomeworks,
    studentHomeworkDetail,
    studentAttendance,
    studentNotices,
    studentLessons,
    studentLessonDetail,
    studentScores,
    studentSchedule,
    studentQna,
    studentQnaDetail,
    studentOnlineTests,
    studentOnlineTest,
    parentHome,
    parentHomeworks,
    parentSchedule,
    parentNotices,
    parentMe,
    parentScores,
    parentReport,
    pushSetting,
  ];

  /// 알림이 왔다 — 무엇이 바뀌었는지 앱은 모른다(payload 에 내용이 없다). 전부
  /// 다음 [ParamController.load] 에 다시 받게 한다. 알림은 하루 몇 건이라
  /// 60초 규칙이 아끼던 요청이 몇 개 늘 뿐이다.
  void markAllStale() {
    for (final c in _screens) {
      c.markStale();
    }
  }

  void dispose() {
    studentHome.dispose();
    studentHomeworks.dispose();
    studentHomeworkDetail.dispose();
    studentAttendance.dispose();
    studentNotices.dispose();
    studentLessons.dispose();
    studentLessonDetail.dispose();
    studentScores.dispose();
    studentSchedule.dispose();
    studentQna.dispose();
    studentQnaDetail.dispose();
    studentOnlineTests.dispose();
    studentOnlineTest.dispose();
    selectedChild.dispose();
    parentHome.dispose();
    parentHomeworks.dispose();
    parentSchedule.dispose();
    parentNotices.dispose();
    parentMe.dispose();
    parentScores.dispose();
    parentReport.dispose();
    pushSetting.dispose();
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
                    builder: (_, _) => StudentSchedulePage(
                      controller: _session.studentSchedule,
                      onClinicChanged: _session.studentClinicChanged,
                    ),
                  ),
                  GoRoute(
                    path: 'online-tests',
                    builder: (_, _) => StudentOnlineTestsPage(
                      controller: _session.studentOnlineTests,
                    ),
                    // 응시는 목록의 자식이다 — 뒤로 가면 목록이고 탭 바가 남는다.
                    routes: [
                      GoRoute(
                        path: ':testId',
                        builder: (_, state) => StudentOnlineTestPage(
                          testId: int.parse(state.pathParameters['testId']!),
                          controller: _session.studentOnlineTest,
                          openUrl: _openUrl,
                          onSubmitted: _session.studentOnlineTestSubmitted,
                        ),
                      ),
                    ],
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
                  pushSetting: _session.pushSetting,
                  onLogout: widget.auth.logout,
                ),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: AppRoutes.studentQna,
                builder: (_, _) => StudentQnaPage(
                  controller: _session.studentQna,
                  uploader: _session.qnaUploader,
                  picker: _mediaPicker,
                ),
                // 상세는 목록의 자식이다 — 뒤로 가면 목록이고 탭 바가 남는다.
                routes: [
                  GoRoute(
                    path: ':postId',
                    builder: (_, state) => StudentQnaDetailPage(
                      postId: int.parse(state.pathParameters['postId']!),
                      controller: _session.studentQnaDetail,
                      uploader: _session.qnaUploader,
                      picker: _mediaPicker,
                      onChanged: _session.studentQna.markStale,
                    ),
                  ),
                ],
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
                  pushSetting: _session.pushSetting,
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

  final _messenger = GlobalKey<ScaffoldMessengerState>();
  final List<StreamSubscription<Object>> _pushSubs = [];

  /// 로그인이 끝나기 전에 눌린 알림(앱이 꺼져 있다가 알림으로 켜진 경우). 부팅
  /// 복원이 `ready` 가 되면 연다. 로그인 화면으로 떨어지면 버린다.
  PushLink? _pendingLink;

  @override
  void initState() {
    super.initState();
    widget.auth.addListener(_onAuthChanged);
    _onAuthChanged();
    _listenPush(widget.push);
  }

  void _listenPush(PushMessaging push) {
    _pushSubs
      ..add(push.onOpened.listen(_openLink))
      ..add(push.onForeground.listen(_onForegroundPush));
    push.initialLink().then((link) {
      if (link != null && mounted) _openLink(link);
    }, onError: (Object e) => debugPrint('[push] 첫 알림 읽기 실패: $e'));
  }

  /// 앱이 떠 있을 때 온 알림. 시스템 알림이 안 뜨므로 스낵바로 알린다 — 열지 않고
  /// 지금 보는 화면만 새로 받는다. 누르면 그 화면으로 간다.
  void _onForegroundPush(ForegroundPush push) {
    if (widget.auth.snapshot.status != AuthStatus.ready) return;
    _pushArrived();
    final title = push.title;
    final link = push.link;
    if (title == null) return;
    final role = widget.auth.snapshot.role;
    final canOpen =
        link != null && role != null && pushLocation(link, role) != null;
    _messenger.currentState
      ?..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(title),
          behavior: SnackBarBehavior.floating,
          action: canOpen
              ? SnackBarAction(label: '보기', onPressed: () => _openLink(link))
              : null,
        ),
      );
  }

  /// 세션의 데이터가 낡았다고 표시하고, 보이는 화면을 새로 받게 한다.
  void _pushArrived() {
    _currentSession?.markAllStale();
    PushArrivals.instance.arrived();
  }

  void _openLink(PushLink link) {
    // 로그아웃된 폰에 남은 알림(토큰을 지우기 전에 온 것)을 누른 것이다. 다음에
    // 로그인하는 사람이 앞 사람의 알림 화면으로 가면 안 된다.
    if (widget.auth.snapshot.status == AuthStatus.loggedOut) return;
    _pendingLink = link;
    _openPendingLink();
  }

  /// 로그인돼 있을 때만 연다. 셸이 그려진 다음 프레임에 옮긴다 — 인증 알림을 받는
  /// 도중에 옮기면 라우터의 리다이렉트와 순서가 엉킨다.
  void _openPendingLink() {
    if (_pendingLink == null) return;
    if (widget.auth.snapshot.status != AuthStatus.ready) return;
    WidgetsBinding.instance
      ..addPostFrameCallback((_) => _goToPendingLink())
      // 알림은 화면이 가만히 있을 때 온다. 프레임을 청하지 않으면 다른 무언가가
      // 다시 그릴 때까지 콜백이 안 불린다.
      ..ensureVisualUpdate();
  }

  Future<void> _goToPendingLink() async {
    final link = _pendingLink;
    final snapshot = widget.auth.snapshot;
    if (!mounted || link == null || snapshot.status != AuthStatus.ready) {
      return;
    }
    _pendingLink = null;
    final role = snapshot.role!;
    final session = _session;
    _pushArrived();
    final location = pushLocation(link, role);
    if (location == null) return;

    final studentId = link.studentId;
    if (role == UserRole.parent && studentId != null) {
      await session.selectedChild.selectIfMine(studentId);
      // 기다리는 사이 로그아웃했다.
      if (!mounted || !identical(session, _currentSession)) return;
    }
    _router.go(location);

    // 공지 상세는 라우트가 아니라 목록 위의 시트다. 목록이 그려진 뒤 띄운다.
    final noticeId = link.screen == 'notice' ? link.id : null;
    if (noticeId == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = _router.routerDelegate.navigatorKey.currentContext;
      if (context == null || !identical(session, _currentSession)) return;
      showNoticeSheet(
        context,
        repository: NoticeRepository(widget.dio),
        noticeId: noticeId,
        studentId: role == UserRole.parent
            ? session.selectedChild.selectedStudentId
            : null,
        openUrl: _openUrl,
      );
    });
    WidgetsBinding.instance.ensureVisualUpdate();
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
    for (final sub in _pushSubs) {
      sub.cancel();
    }
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
        // 로그인 화면으로 떨어졌다. 다음 사람에게 앞 사람의 알림을 열어 주지 않는다.
        _pendingLink = null;
      case AuthStatus.ready:
        _currentSession ??= _Session(dio: widget.dio, store: widget.store);
        _openPendingLink();
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
      scaffoldMessengerKey: _messenger,
    );
  }
}
