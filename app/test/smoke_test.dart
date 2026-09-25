import 'dart:convert';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/login_response.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/features/auth/password_change_page.dart';
import 'package:academy_app/features/auth/privacy_page.dart';
import 'package:academy_app/features/auth/teacher_notice_page.dart';
import 'package:academy_app/features/auth/terms_page.dart';
import 'package:academy_app/main.dart';
import 'package:academy_app/shared/widgets/bottom_tab_bar.dart';
import 'package:academy_app/shared/widgets/full_screen_loader.dart';
import 'package:academy_app/shared/widgets/role_shell.dart';
import 'package:academy_app/shared/widgets/section.dart';

/// 역할 → 화면 배선만 확인하면 되므로 `me()`만 채운다.
/// `auth_controller_test.dart`의 `_FakeRepo` 패턴을 그대로 따른다.
class _FakeAuthRepo implements AuthRepository {
  _FakeAuthRepo(this._meResult);

  final MeResponse _meResult;

  @override
  Future<MeResponse> me() async => _meResult;

  @override
  Future<LoginResponse> login({
    required String loginId,
    required String password,
  }) => throw UnimplementedError('스모크 테스트는 로그인을 거치지 않는다');

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) => throw UnimplementedError('스모크 테스트는 비밀번호 변경을 거치지 않는다');

  @override
  Future<void> logout() async {}

  @override
  Future<SignupResponse> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) => throw UnimplementedError('스모크 테스트는 가입을 거치지 않는다');
}

/// 경로로 답하는 어댑터. 학부모 쪽은 자녀 목록 → 그 아이의 홈 두 번을
/// 부르는데, 순서 대본(FakeAdapter)은 부르는 순서가 바뀌면 엉뚱한 응답을
/// 준다 — 여기서는 경로가 곧 대본이다. 대본에 없는 경로는 던진다.
class _RoutingAdapter implements HttpClientAdapter {
  _RoutingAdapter(this.routes);

  final Map<String, Map<String, dynamic>> routes;
  final List<String> received = [];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    received.add(options.path);
    final body = routes[options.path];
    if (body == null) {
      throw StateError('대본에 없는 요청이다: ${options.method} ${options.path}');
    }
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// 학부모 홈(P-1). 눌러 볼 대상(공지 줄)이 있어야 하므로 하나 깐다.
const _parentHome = {
  'success': true,
  'data': {
    'student': {'name': '김하늘'},
    'nextExam': null,
    'nextLessonDate': '2026-09-21',
    'nextLessonTime': '19:00',
    'nextLessonDDay': 2,
    'notices': {
      'totalCount': 1,
      'recent': [
        {
          'noticeId': 1,
          'title': '추석 휴원 안내',
          'pinned': false,
          'hasAttachment': false,
          'publishedAt': '2026-09-15T10:00:00+09:00',
        },
      ],
    },
    'pendingHomeworkCount': 2,
    'nextClinic': null,
    'thisMonthAttendance': {
      'present': 6,
      'late': 1,
      'absent': 0,
      'sick': 0,
      'excused': 0,
      'makeup': 2,
    },
  },
};

/// 학생 홈(S-1)과 학부모 홈(P-1)이 부르는 API. 눌러 볼 대상(숙제 줄·공지 줄·
/// 지난 수업)이 있어야 하므로 하나씩 깐다.
Dio _fakeDio() {
  const home = {
    'success': true,
    'data': {
      'student': {'name': '김하늘'},
      'nextLesson': null,
      'nextExam': null,
      'nextClinic': null,
      'currentHomeworks': [
        {
          'homeworkId': 11,
          'title': '단어 3과',
          'dueAt': '2026-09-21T21:00:00+09:00',
          'status': 'NOT_SUBMITTED',
          'remainingMinutes': 1500,
        },
      ],
      'lastLesson': {
        'lessonId': 3,
        'lessonDate': '2026-09-14',
        'title': '관계대명사',
        'videoId': null,
        'embedUrl': null,
        'videoCount': 0,
        'content': '관계대명사 that',
        'homeworkNote': null,
      },
      'notices': {
        'totalCount': 1,
        'recent': [
          {
            'noticeId': 1,
            'title': '추석 휴원 안내',
            'pinned': false,
            'hasAttachment': false,
            'publishedAt': '2026-09-15T10:00:00+09:00',
          },
        ],
      },
    },
  };
  return Dio(BaseOptions(baseUrl: 'https://example.test'))
    ..httpClientAdapter = _RoutingAdapter({
      '/api/student/home': home,
      '/api/parent/children': {
        'success': true,
        'data': [
          {'studentId': 1, 'name': '김하늘'},
        ],
      },
      '/api/parent/children/1/home': _parentHome,
    });
}

/// 토큰을 미리 심고 `bootstrap()`으로 `me()`를 태워 해당 역할·상태의
/// `AuthSnapshot`을 만든 뒤 `AcademyApp`을 그 상태로 띄운다.
Future<void> _pumpAtRole(
  WidgetTester tester, {
  required UserRole role,
  bool mustChangePassword = false,
}) async {
  final kv = InMemoryKeyValueStore();
  final tokens = TokenStore(kv);
  await tokens.write('at-1');
  final auth = AuthController(
    repository: _FakeAuthRepo(
      MeResponse(
        id: 1,
        name: '김하늘',
        role: role,
        phone: '01012345678',
        mustChangePassword: mustChangePassword,
      ),
    ),
    tokens: tokens,
    cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
    jar: CookieJar(),
  );

  await auth.bootstrap();
  await tester.pumpWidget(AcademyApp(auth: auth, dio: _fakeDio(), store: kv));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('부팅하면 splash가 뜬다', (tester) async {
    // 부팅 전 상태는 unknown 이고 라우터가 splash 로 보낸다.
    // 이게 없으면 이미 로그인한 사용자가 매번 로그인 화면을 깜빡 본다.
    final kv = InMemoryKeyValueStore();
    final auth = AuthController(
      repository: AuthRepository(Dio()),
      tokens: TokenStore(kv),
      cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
      jar: CookieJar(),
    );

    await tester.pumpWidget(AcademyApp(auth: auth, dio: _fakeDio(), store: kv));
    await tester.pump();

    expect(find.byType(FullScreenLoader), findsOneWidget);
  });

  test('Android 에뮬레이터 기본 baseUrl은 10.0.2.2다', () {
    // localhost 를 쓰면 에뮬레이터 자기 자신을 가리켜 조용히 실패한다.
    // 이 테스트는 호스트에서 돌아 Platform.isAndroid 가 false 이므로
    // 값이 아니라 함수가 존재하고 빈 문자열을 주지 않는 것만 확인한다.
    expect(resolveBaseUrl(), isNotEmpty);
    expect(resolveBaseUrl(), startsWith('http'));
  });

  // 역할 → 화면 배선. RoleShell 은 학생·학부모가 공유하므로 타입만으로는
  // 구분할 수 없다 — 역할 칩 글자(AppBarBand 가 그린다)로 구분한다.
  // 라우트 테이블이 main.dart 안에 있어서 redirectFor(순수 함수)만으로는
  // 이 배선을 검증할 수 없었다. 학생·학부모 빌더를 바꿔치기해도 나머지
  // 테스트가 전부 그린이었던 이유다. 학부모가 학생 화면에 앉는 사고가 여기 걸린다.
  group('역할이 맞는 화면으로 연결된다', () {
    testWidgets('ready + student → RoleShell(학생), /student 에 앉는다', (
      tester,
    ) async {
      await _pumpAtRole(tester, role: UserRole.student);

      expect(find.byType(RoleShell), findsOneWidget);
      expect(find.text('학생'), findsOneWidget);
      expect(find.text('학부모'), findsNothing);
      expect(find.byType(TeacherNoticePage), findsNothing);

      final router = GoRouter.of(tester.element(find.byType(RoleShell)));
      expect(router.routerDelegate.currentConfiguration.uri.path, '/student');
    });

    testWidgets('하단 탭 바의 다섯 라벨이 보인다', (tester) async {
      await _pumpAtRole(tester, role: UserRole.student);

      // **탭 바 안에서 센다.** 홈의 퀵 레일에 같은 라벨이 넷 더 있다
      // (숙제·수업·성적·질문) — 겹치는 것은 의도다. 그냥 find.text 로
      // 세면 이 테스트가 레일 때문에 깨진다.
      for (final label in ['홈', '숙제', '수업', '성적', '질문']) {
        expect(
          find.descendant(
            of: find.byType(BottomTabBar),
            matching: find.text(label),
          ),
          findsOneWidget,
        );
      }
    });

    testWidgets('학생 셸의 성적 탭에 로그아웃이 있다', (tester) async {
      // 로그아웃 경로가 끊기지 않았는지 지키는 테스트다. 자리표시자 셸이
      // 들고 있던 것을 스텁으로 옮겼으므로 여기서 확인한다.
      await _pumpAtRole(tester, role: UserRole.student);
      // 라벨이 홈의 퀵 레일에도 있으므로 탭 바의 칸을 키로 누른다.
      await tester.tap(find.byKey(const ValueKey('tab-/student/scores')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('student-logout')), findsOneWidget);
    });

    testWidgets('ready + parent → RoleShell(학부모), /parent 에 앉는다', (
      tester,
    ) async {
      await _pumpAtRole(tester, role: UserRole.parent);

      expect(find.byType(RoleShell), findsOneWidget);
      expect(find.text('학부모'), findsOneWidget);
      expect(find.text('학생'), findsNothing);

      final router = GoRouter.of(tester.element(find.byType(RoleShell)));
      expect(router.routerDelegate.currentConfiguration.uri.path, '/parent');
    });

    testWidgets('ready + teacher → TeacherNoticePage', (tester) async {
      await _pumpAtRole(tester, role: UserRole.teacher);

      expect(find.byType(TeacherNoticePage), findsOneWidget);
      expect(find.byType(RoleShell), findsNothing);
    });

    testWidgets('mustChangePassword → PasswordChangePage', (tester) async {
      await _pumpAtRole(
        tester,
        role: UserRole.student,
        mustChangePassword: true,
      );

      expect(find.byType(PasswordChangePage), findsOneWidget);
      expect(find.byType(RoleShell), findsNothing);
    });
  });

  testWidgets('약관·처리방침 라우트가 실제 화면을 그린다', (tester) async {
    // main.dart 의 실제 라우트 테이블을 쓰는 파일은 이 스모크 테스트뿐이다.
    // /terms·/privacy 를 방문하지 않으면 라우트가 지워져도 여기서는 안 보인다.
    await _pumpAtRole(tester, role: UserRole.student);

    // GoRouter 자체(엘리먼트가 아니라 라우터 객체)를 한 번만 얻어 재사용한다
    // — RoleShell 은 이동에서 트리를 떠나 그 컨텍스트가 비활성화된다.
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.terms);
    await tester.pumpAndSettle();
    expect(find.byType(TermsPage), findsOneWidget);

    router.go(AppRoutes.privacy);
    await tester.pumpAndSettle();
    expect(find.byType(PrivacyPage), findsOneWidget);
  });

  testWidgets('홈 퀵 레일의 여덟 칸이 모두 라우트로 간다', (tester) async {
    // 레일은 8칸인데 탭은 다섯이다. 나머지 넷(클리닉·온라인테스트·출석·공지)은
    // AppRoutes 에 상수만 있고 **라우트가 없었다** — 누르면 go_router 오류
    // 화면이 떴다. 화면 단위 테스트로는 안 드러나고 실제 라우트 테이블을 쓰는
    // 여기서만 드러난다.
    //
    // RoleShell 이 남아 있는지도 함께 본다. 넷은 셸 **안**의 자식 라우트라
    // 하단 탭 바가 그대로 있어야 하고, 라우트가 없으면 오류 화면이 셸째로
    // 덮으므로 이 단언이 그것까지 잡는다.
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    const routes = [
      AppRoutes.studentHomeworks,
      AppRoutes.studentLessons,
      AppRoutes.studentClinics,
      AppRoutes.studentScores,
      AppRoutes.studentOnlineTests,
      AppRoutes.studentAttendances,
      AppRoutes.studentNotices,
      AppRoutes.studentQna,
    ];

    for (final route in routes) {
      router.go(AppRoutes.student);
      await tester.pumpAndSettle();

      // 마지막 칸은 카드 끝에서 잘려 있다(그게 「옆으로 넘길 수 있다」의
      // 유일한 신호다) — 누르기 전에 레일을 굴린다.
      final tile = find.byKey(Key('rail-$route'));
      await tester.ensureVisible(tile);
      await tester.pumpAndSettle();
      await tester.tap(tile);
      await tester.pumpAndSettle();

      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        route,
        reason: '$route 로 가야 한다',
      );
      expect(find.byType(RoleShell), findsOneWidget, reason: '$route 가 셸 밖이다');
    }
  });

  testWidgets('홈의 숙제 줄·구획 머리·공지가 제자리로 간다', (tester) async {
    // 레일 말고도 눌리는 곳이 있다. 숙제 줄은 레일 다음으로 많이 눌리고
    // (B1 에는 상세가 없어 숙제 목록으로 간다 — 브리프 정정 §4), 구획 머리의
    // 「전체 ›」는 구획마다 목적지가 다르다. 콜백을 바꿔 끼워도 화면 테스트는
    // 모르므로 실제 라우터로 누른다.
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    /// 그 구획 머리의 「전체 ›」. 「미완료 숙제」는 지면 칸 라벨로도 나오지만
    /// 그쪽은 SectionHead 밑이 아니라 여기 걸리지 않는다.
    Finder headAction(String title) => find.descendant(
      of: find.ancestor(
        of: find.text(title),
        matching: find.byType(SectionHead),
      ),
      matching: find.text('전체 ›'),
    );

    final cases = <(String, Finder, String)>[
      (
        '숙제 줄',
        find.byKey(const Key('homework-row')),
        AppRoutes.studentHomeworks,
      ),
      ('미완료 숙제 머리', headAction('미완료 숙제'), AppRoutes.studentHomeworks),
      ('공지 머리', headAction('학원 공지'), AppRoutes.studentNotices),
      ('공지 줄', find.byKey(const Key('notice-row')), AppRoutes.studentNotices),
      ('지난 수업 머리', headAction('지난 수업'), AppRoutes.studentLessons),
    ];

    for (final (name, target, route) in cases) {
      router.go(AppRoutes.student);
      await tester.pumpAndSettle();
      expect(target, findsOneWidget, reason: '$name 이 홈에 없다');
      await tester.ensureVisible(target);
      await tester.pumpAndSettle();
      await tester.tap(target);
      await tester.pumpAndSettle();
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        route,
        reason: '$name 은 $route 로 가야 한다',
      );
      expect(find.byType(RoleShell), findsOneWidget, reason: '$name 이 셸 밖이다');
    }
  });

  testWidgets('학부모 홈 퀵 레일의 여섯 칸이 모두 라우트로 간다', (tester) async {
    // 레일은 6칸인데 탭은 다섯이다. 숙제·공지는 AppRoutes 에 상수만 있고
    // 라우트가 없었다 — 누르면 go_router 오류 화면이 떴다.
    //
    // **라우터로 가지 않고 칸을 실제로 누른다.** 레일은 지면 위로
    // Transform 으로 끌어올려져 있어서, 겹침 Column 을 ListView 자식으로
    // 펴거나 RepaintBoundary 를 끼우면 그려진 칸 대부분이 손가락에 안 닿는다
    // (학생 홈에서 실제로 겪었다). 여기가 그것을 잡는 유일한 곳이다.
    await _pumpAtRole(tester, role: UserRole.parent);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    const routes = [
      AppRoutes.parentSchedule,
      AppRoutes.parentHomeworks,
      AppRoutes.parentScores,
      AppRoutes.parentLessons,
      AppRoutes.parentNotices,
      AppRoutes.parentMe,
    ];

    for (final route in routes) {
      router.go(AppRoutes.parent);
      await tester.pumpAndSettle();

      final tile = find.byKey(Key('rail-$route'));
      await tester.ensureVisible(tile);
      await tester.pumpAndSettle();
      await tester.tap(tile);
      await tester.pumpAndSettle();

      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        route,
        reason: '$route 로 가야 한다',
      );
      expect(find.byType(RoleShell), findsOneWidget, reason: '$route 가 셸 밖이다');
    }
  });

  testWidgets('학부모 홈의 구획 머리·공지 줄이 제자리로 간다', (tester) async {
    await _pumpAtRole(tester, role: UserRole.parent);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    Finder headAction(String title) => find.descendant(
      of: find.ancestor(
        of: find.text(title),
        matching: find.byType(SectionHead),
      ),
      matching: find.text('전체 ›'),
    );

    final cases = <(String, Finder, String)>[
      ('숙제 머리', headAction('숙제'), AppRoutes.parentHomeworks),
      ('이번 달 출석 머리', headAction('이번 달 출석'), AppRoutes.parentSchedule),
      ('공지 머리', headAction('학원 공지'), AppRoutes.parentNotices),
      ('공지 줄', find.byKey(const Key('notice-row')), AppRoutes.parentNotices),
    ];

    for (final (name, target, route) in cases) {
      router.go(AppRoutes.parent);
      await tester.pumpAndSettle();
      expect(target, findsOneWidget, reason: '$name 이 홈에 없다');
      await tester.ensureVisible(target);
      await tester.pumpAndSettle();
      await tester.tap(target);
      await tester.pumpAndSettle();
      expect(
        router.routerDelegate.currentConfiguration.uri.path,
        route,
        reason: '$name 은 $route 로 가야 한다',
      );
      expect(find.byType(RoleShell), findsOneWidget, reason: '$name 이 셸 밖이다');
    }
  });
}
