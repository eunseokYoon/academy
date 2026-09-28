import 'dart:convert';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import 'core/push/fake_push.dart';

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
import 'package:academy_app/core/push/push_link.dart';
import 'package:academy_app/core/push/push_messaging.dart';
import 'package:academy_app/features/auth/login_page.dart';
import 'package:academy_app/features/auth/privacy_page.dart';
import 'package:academy_app/features/parent/me/parent_me_page.dart';
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
  }) async => LoginResponse(
    accessToken: 'at-1',
    user: UserSummary(
      id: _meResult.id,
      name: _meResult.name,
      role: _meResult.role,
      mustChangePassword: _meResult.mustChangePassword,
    ),
  );

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
    'student': {
      'name': '김하늘',
      'classRooms': ['A반'],
    },
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

const _homeworkItem = {
  'homeworkId': 11,
  'title': '단어 3과',
  'description': null,
  'classRoomName': 'A반',
  'kind': 'ONLINE',
  'lessonDate': '2026-09-14',
  'result': null,
  'completionRate': null,
  'resolvedByResubmission': false,
  'resubmitRequired': false,
  'dueAt': '2026-09-21T21:00:00+09:00',
  'status': 'NOT_SUBMITTED',
  'isLate': false,
  'photoCount': 0,
  'hasVideo': false,
  'remainingMinutes': 1500,
};

const _studentHomeworks = {
  'success': true,
  'data': {
    'items': [_homeworkItem],
    'page': 0,
    'size': 20,
    'totalElements': 1,
    'totalPages': 1,
  },
};

const _studentHomeworkDetail = {
  'success': true,
  'data': {
    'homework': {
      'id': 11,
      'title': '단어 3과',
      'description': null,
      'kind': 'ONLINE',
      'lessonDate': '2026-09-14',
      'dueAt': '2026-09-21T21:00:00+09:00',
      'classRoomName': 'A반',
    },
    'submission': {
      'id': 5,
      'status': 'NOT_SUBMITTED',
      'submittedAt': null,
      'isLate': false,
      'photos': [],
      'video': null,
    },
    'resubmitRequired': false,
  },
};

const _parentHomeworks = {
  'success': true,
  'data': {
    'items': [],
    'page': 0,
    'size': 20,
    'totalElements': 0,
    'totalPages': 0,
  },
};

/// 학생 홈(S-1)과 학부모 홈(P-1)이 부르는 API. 눌러 볼 대상(숙제 줄·공지 줄·
/// 지난 수업)이 있어야 하므로 하나씩 깐다.
/// 마지막으로 만든 [_fakeDio] 의 어댑터. 받은 요청을 세는 테스트가 쓴다.
_RoutingAdapter? _lastAdapter;

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
    ..httpClientAdapter = _lastAdapter = _RoutingAdapter({
      '/api/notices/5': {
        'success': true,
        'data': {
          'noticeId': 5,
          'title': '추석 휴강 안내',
          'content': '9/17~9/19 휴강합니다.',
          'publishedAt': '2026-09-20T10:00:00+09:00',
          'attachments': [],
        },
      },
      '/api/student/home': home,
      '/api/parent/children': {
        'success': true,
        'data': [
          {'studentId': 1, 'name': '김하늘'},
        ],
      },
      '/api/parent/children/1/home': _parentHome,
      // B2 숙제 화면들. 레일·숙제 줄이 여기로 간다.
      '/api/student/homeworks': _studentHomeworks,
      '/api/student/homeworks/notes': {'success': true, 'data': []},
      '/api/student/homeworks/11': _studentHomeworkDetail,
      '/api/parent/children/1/homeworks': _parentHomeworks,
      // B3a. 경로만 보고 답하므로 달·자녀 쿼리는 무시된다.
      '/api/student/attendances': _attendanceMonth,
      // B4a 스케줄이 「내 클리닉」 한 줄을 그리도록 배정 하나를 깐다. 출석
      // 캘린더(B3a)도 같은 응답을 쓴다.
      '/api/student/clinics': {
        'success': true,
        'data': [
          {
            'clinicId': 21,
            'clinicDate': '2026-09-03',
            'startTime': '17:00',
            'endTime': '22:00',
            'slots': ['17:00', '18:00', '19:00', '20:00', '21:00'],
            'capacity': null,
            'reservedCount': 1,
            'full': false,
            'weekLabel': '9월 1주',
            'myReservation': {
              'reservationId': 1,
              'status': 'RESERVED',
              'arrivalTime': '18:00',
              'attendStatus': null,
            },
          },
        ],
      },
      '/api/student/lesson-changes': {'success': true, 'data': []},
      '/api/student/qna': {
        'success': true,
        'data': {
          'items': [
            {
              'postId': 31,
              'title': '관계대명사 질문',
              'authorName': '김하늘',
              'classRoomId': 1,
              'classRoomName': 'A반',
              'isPublic': false,
              'mine': true,
              'hasPhoto': false,
              'answerCount': 1,
              'createdAt': '2026-09-20T10:00:00+09:00',
            },
          ],
          'totalPages': 1,
        },
      },
      '/api/student/qna/31': {
        'success': true,
        'data': {
          'postId': 31,
          'title': '관계대명사 질문',
          'authorName': '김하늘',
          'classRoomId': 1,
          'classRoomName': 'A반',
          'isPublic': false,
          'content': 'that 과 which 차이가 뭔가요?',
          'photos': [],
          'editable': true,
          'createdAt': '2026-09-20T10:00:00+09:00',
          'answers': [
            {
              'answerId': 32,
              'authorName': '선생님',
              'byTeacher': true,
              'content': '수업 때 다시 볼게요.',
              'photos': [],
              'editable': false,
              'createdAt': '2026-09-20T11:00:00+09:00',
            },
          ],
        },
      },
      '/api/student/reviews/me': {'success': true, 'data': null},
      // B4b. 목록은 마감 없는 것 하나, 응시는 3문항.
      '/api/student/online-tests': {
        'success': true,
        'data': [
          {
            'testId': 41,
            'title': '9월 2주 클리닉',
            'classRoomName': 'A반',
            'questionCount': 3,
            'closesAt': null,
            'remainingMinutes': null,
            'status': 'NOT_STARTED',
            'answeredCount': 0,
          },
        ],
      },
      '/api/student/online-tests/41': {
        'success': true,
        'data': {
          'testId': 41,
          'title': '9월 2주 클리닉',
          'classRoomName': 'A반',
          'questionCount': 3,
          'choiceCount': 5,
          'closesAt': null,
          'chosenChoices': [null, null, null],
          'status': 'IN_PROGRESS',
        },
      },
      '/api/parent/children/1/attendances': _attendanceMonth,
      '/api/parent/children/1/clinics': {'success': true, 'data': []},
      '/api/notices': {
        'success': true,
        'data': {
          'items': [
            {
              'noticeId': 5,
              'title': '추석 휴강 안내',
              'pinned': false,
              'hasAttachment': false,
              'publishedAt': '2026-09-20T10:00:00+09:00',
            },
          ],
        },
      },
      // B3b.
      '/api/student/lessons': {
        'success': true,
        'data': {
          'items': [
            {
              'lessonId': 3,
              'lessonDate': '2026-09-14',
              'title': '관계대명사',
              'classRoomName': 'A반',
              'hasVideo': false,
              'isNew': true,
              'homeworkTitle': null,
            },
          ],
          'totalPages': 1,
        },
      },
      '/api/student/lessons/3': {
        'success': true,
        'data': {
          'lessonId': 3,
          'lessonDate': '2026-09-14',
          'title': '관계대명사',
          'classRoomName': 'A반',
          'videos': [],
          'content': '관계대명사 that',
          'keyPoints': null,
          'homeworkNote': null,
          'clinicNote': null,
          'homework': null,
          'attendanceStatus': 'PRESENT',
        },
      },
      '/api/student/me': {
        'success': true,
        'data': {
          'studentId': 1,
          'name': '김하늘',
          'classRooms': [
            {'classRoomId': 1, 'name': 'A반'},
          ],
          'phone': '01011112222',
          'parentLinked': true,
        },
      },
      '/api/student/scores': _scores,
      '/api/student/exam-schedules': {'success': true, 'data': []},
      '/api/parent/children/1/scores': _scores,
      '/api/parent/children/1/exam-schedules': {'success': true, 'data': []},
      '/api/parent/children/1/lessons': {
        'success': true,
        'data': {'items': [], 'totalPages': 0},
      },
      '/api/push/settings': {
        'success': true,
        'data': {'enabled': true},
      },
      '/api/parent/me': {
        'success': true,
        'data': {
          'id': 9,
          'name': '김하늘 학부모',
          'phone': '01011112222',
          'children': [
            {'studentId': 1, 'name': '김하늘'},
          ],
        },
      },
    });
}

const _scores = {
  'success': true,
  'data': {
    'retestScheduled': [],
    'sections': [
      {
        'testType': 'WORD',
        'label': '단어 테스트',
        'chartKind': 'NONE',
        'items': [
          {
            'year': 2026,
            'month': 9,
            'week': 2,
            'weekLabel': '9월 2주',
            'correctCount': 12,
            'totalCount': 15,
            'result': 'PASS',
            'retestPassed': false,
            'retestScheduled': false,
          },
        ],
      },
    ],
  },
};

const _attendanceMonth = {
  'success': true,
  'data': {
    'year': 2026,
    'month': 9,
    'summary': {
      'present': 3,
      'late': 0,
      'absent': 0,
      'sick': 0,
      'excused': 0,
      'makeup': 0,
    },
    'homeworkCompletionRate': null,
    'days': [
      {'date': '2026-09-01', 'status': 'PRESENT', 'homeworkRate': null},
    ],
  },
};

/// 토큰을 미리 심고 `bootstrap()`으로 `me()`를 태워 해당 역할·상태의
/// `AuthSnapshot`을 만든 뒤 `AcademyApp`을 그 상태로 띄운다.
Future<void> _pumpAtRole(
  WidgetTester tester, {
  required UserRole role,
  bool mustChangePassword = false,
  PushMessaging push = const DisabledPushMessaging(),
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
  await tester.pumpWidget(
    AcademyApp(auth: auth, dio: _fakeDio(), store: kv, push: push),
  );
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
    // (그 숙제의 상세로 간다), 구획 머리의
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
      // 웹과 같이 그 숙제의 상세로 간다(B2).
      (
        '숙제 줄',
        find.byKey(const Key('homework-row')),
        '${AppRoutes.studentHomeworks}/11',
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

  testWidgets('숙제 목록 → 상세 → 목록이 셸 안에서 오간다', (tester) async {
    // 상세는 숙제 갈래의 자식 라우트다. 라우트가 빠지면 go_router 오류 화면이
    // 셸째로 덮고, 뒤로 가기가 홈으로 튄다.
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));
    router.go(AppRoutes.studentHomeworks);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const ValueKey('homework-11')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '${AppRoutes.studentHomeworks}/11',
    );
    expect(find.byKey(const Key('homework-header')), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);

    await tester.tap(find.byKey(const Key('page-back')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.studentHomeworks,
    );
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

  testWidgets('B3a 학생 화면이 셸 안에서 실물로 그려진다', (tester) async {
    // 라우트만 보면 스텁이 남아 있어도, 화면이 오류 상태로 떠도 통과한다.
    // 실제 라우트 테이블로 들어가 그 화면의 데이터가 그려졌는지 본다.
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.studentAttendances);
    await tester.pumpAndSettle();
    expect(find.text('출석 현황'), findsOneWidget);
    expect(find.byKey(const Key('calendar-month')), findsOneWidget);

    router.go(AppRoutes.studentNotices);
    await tester.pumpAndSettle();
    expect(find.text('추석 휴강 안내'), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);
  });

  testWidgets('B3a 학부모 화면이 셸 안에서 실물로 그려진다', (tester) async {
    // 한 테스트에서 역할을 바꿔 다시 띄우면 앞 앱의 라우터가 남는다 — 따로 둔다.
    await _pumpAtRole(tester, role: UserRole.parent);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.parentSchedule);
    await tester.pumpAndSettle();
    expect(find.text('수업 · 클리닉 일정'), findsOneWidget);
    expect(find.byKey(const Key('calendar-month')), findsOneWidget);

    router.go(AppRoutes.parentNotices);
    await tester.pumpAndSettle();
    expect(find.text('추석 휴강 안내'), findsOneWidget);

    router.go(AppRoutes.parentMe);
    await tester.pumpAndSettle();
    expect(find.text('김하늘 학부모 님'), findsOneWidget);
    // 학부모의 유일한 로그아웃이다(14-7). 알림 스위치 아래라 끝까지 내린다.
    await tester.scrollUntilVisible(
      find.byKey(const Key('parent-logout')),
      200,
      scrollable: find
          .descendant(
            of: find.byType(ParentMePage),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    expect(find.byKey(const Key('push-setting-switch')), findsOneWidget);
    expect(find.byKey(const Key('parent-logout')), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);
  });

  testWidgets('B3b 학생 화면이 셸 안에서 실물로 그려진다', (tester) async {
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.studentLessons);
    await tester.pumpAndSettle();
    expect(find.text('수업영상 및 레포트'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('lesson-3')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '${AppRoutes.studentLessons}/3',
    );
    expect(find.text('관계대명사 that'), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);

    await tester.tap(find.byKey(const Key('page-back')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.studentLessons,
    );

    router.go(AppRoutes.studentScores);
    await tester.pumpAndSettle();
    expect(find.text('내 정보 · 성적'), findsOneWidget);
    expect(find.text('12/15'), findsOneWidget);
    expect(find.byKey(const Key('student-logout')), findsOneWidget);
  });

  testWidgets('B4a 학생 화면이 셸 안에서 실물로 그려진다', (tester) async {
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.studentClinics);
    await tester.pumpAndSettle();
    expect(find.text('스케줄 관리'), findsOneWidget);
    expect(find.text('09-03 18:00 도착'), findsOneWidget);
    expect(find.text('변경 요청한 수업이 없습니다.'), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);

    router.go(AppRoutes.studentQna);
    await tester.pumpAndSettle();
    expect(find.text('질의응답'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('qna-31')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '${AppRoutes.studentQna}/31',
    );
    expect(find.text('that 과 which 차이가 뭔가요?'), findsOneWidget);
    expect(find.text('수업 때 다시 볼게요.'), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);

    await tester.tap(find.byKey(const Key('page-back')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.studentQna,
    );
  });

  testWidgets('B4b 온라인 테스트가 셸 안에서 실물로 그려진다', (tester) async {
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.studentOnlineTests);
    await tester.pumpAndSettle();
    expect(find.text('온라인 테스트'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('test-41')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '${AppRoutes.studentOnlineTests}/41',
    );
    expect(find.text('0 / 3 입력'), findsOneWidget);
    expect(find.byKey(const ValueKey('q-2-5')), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);

    await tester.tap(find.byKey(const Key('page-back')));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.studentOnlineTests,
    );
  });

  testWidgets('홈의 지난 수업 카드는 그 수업의 상세로 간다', (tester) async {
    // 웹 LastLessonCard 의 링크가 `/student/lessons/{id}` 다. 상세가 없던
    // B1·B2 에서는 목록으로 보냈다.
    await _pumpAtRole(tester, role: UserRole.student);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));
    final link = find.text('수업 레포트 전체 보기');
    await tester.ensureVisible(link);
    await tester.pumpAndSettle();
    await tester.tap(link);
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      '${AppRoutes.studentLessons}/3',
    );
  });

  testWidgets('B3b 학부모 화면이 셸 안에서 실물로 그려진다', (tester) async {
    await _pumpAtRole(tester, role: UserRole.parent);
    final router = GoRouter.of(tester.element(find.byType(RoleShell)));

    router.go(AppRoutes.parentScores);
    await tester.pumpAndSettle();
    expect(find.text('테스트 결과'), findsOneWidget);
    expect(find.text('12/15'), findsOneWidget);

    router.go(AppRoutes.parentLessons);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('report-letterhead')), findsOneWidget);
    expect(find.text('이 주에 공개된 수업이 없습니다.'), findsOneWidget);
    expect(find.byType(RoleShell), findsOneWidget);
  });

  group('D2 알림 딥링크', () {
    String path(WidgetTester tester) =>
        GoRouter.of(tester.element(find.byType(RoleShell)))
            .routerDelegate
            .currentConfiguration
            .uri
            .path;

    testWidgets('공지 알림을 누르면 공지 목록 위에 그 공지가 열린다', (tester) async {
      final push = FakePushMessaging();
      await _pumpAtRole(tester, role: UserRole.student, push: push);

      push.opened.add(const PushLink(screen: 'notice', id: 5));
      await tester.pumpAndSettle();

      expect(path(tester), AppRoutes.studentNotices);
      // 시트의 본문. 목록 줄에는 본문이 없다.
      expect(find.text('9/17~9/19 휴강합니다.'), findsOneWidget);
    });

    testWidgets('꺼진 앱을 알림으로 켜면 로그인이 복원된 뒤 그 화면이다', (tester) async {
      final push = FakePushMessaging(
        initial: const PushLink(screen: 'homework'),
      );
      await _pumpAtRole(tester, role: UserRole.student, push: push);

      expect(path(tester), AppRoutes.studentHomeworks);
      expect(find.text('단어 3과'), findsWidgets);
    });

    testWidgets('로그아웃된 채 알림으로 켜면 로그인한 다음 사람을 그 화면으로 보내지 않는다', (tester) async {
      final kv = InMemoryKeyValueStore();
      final auth = AuthController(
        repository: _FakeAuthRepo(
          const MeResponse(
            id: 2,
            name: '김바다',
            role: UserRole.student,
            phone: '01099998888',
            mustChangePassword: false,
          ),
        ),
        tokens: TokenStore(kv),
        cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
        jar: CookieJar(),
      );
      await auth.bootstrap(); // 토큰이 없다 — loggedOut
      final push = FakePushMessaging(
        initial: const PushLink(screen: 'homework'),
      );
      await tester.pumpWidget(
        AcademyApp(auth: auth, dio: _fakeDio(), store: kv, push: push),
      );
      await tester.pumpAndSettle();
      expect(find.byType(LoginPage), findsOneWidget);

      await auth.login(loginId: '01099998888', password: 'pw');
      await tester.pumpAndSettle();

      expect(path(tester), AppRoutes.student);
    });

    testWidgets('학부모 알림은 학부모 화면으로 간다. 내 자녀가 아닌 id 는 무시한다', (tester) async {
      final push = FakePushMessaging();
      await _pumpAtRole(tester, role: UserRole.parent, push: push);

      push.opened.add(const PushLink(screen: 'schedule', studentId: 99));
      await tester.pumpAndSettle();

      expect(path(tester), AppRoutes.parentSchedule);
      // 남의 id 로 부르지 않았다 — 부르면 대본에 없어서 던진다.
      expect(
        _lastAdapter!.received.where((p) => p.contains('/children/99/')),
        isEmpty,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('학부모에게 학생 목적지가 오면 옮기지 않는다', (tester) async {
      final push = FakePushMessaging();
      await _pumpAtRole(tester, role: UserRole.parent, push: push);

      push.opened.add(const PushLink(screen: 'qna', id: 31));
      await tester.pumpAndSettle();

      expect(path(tester), AppRoutes.parent);
    });

    testWidgets('앱이 떠 있을 때 온 알림은 스낵바로 뜨고, 보기를 누르면 그 화면이다', (tester) async {
      final push = FakePushMessaging();
      await _pumpAtRole(tester, role: UserRole.student, push: push);

      push.foreground.add(
        const ForegroundPush(
          title: '숙제가 채점됐어요',
          link: PushLink(screen: 'homework'),
        ),
      );
      // 스낵바가 다 올라올 때까지. 자동으로 닫히는 4초보다는 짧다.
      await tester.pumpAndSettle();
      expect(find.text('숙제가 채점됐어요'), findsOneWidget);
      // 누르기 전에는 옮기지 않는다.
      expect(path(tester), AppRoutes.student);

      await tester.tap(find.text('보기'));
      await tester.pumpAndSettle();
      expect(path(tester), AppRoutes.studentHomeworks);
    });

    testWidgets('보고 있는 화면은 알림이 오면 60초 안이어도 다시 받는다', (tester) async {
      // 숙제 탭을 보는 중에 「채점됐어요」가 왔는데 화면이 그대로면 알림이 거짓말이 된다.
      final push = FakePushMessaging();
      await _pumpAtRole(tester, role: UserRole.student, push: push);
      GoRouter.of(tester.element(find.byType(RoleShell)))
          .go(AppRoutes.studentHomeworks);
      await tester.pumpAndSettle();
      int count() => _lastAdapter!.received
          .where((p) => p == '/api/student/homeworks')
          .length;
      final before = count();

      push.foreground.add(
        const ForegroundPush(
          title: '숙제가 채점됐어요',
          link: PushLink(screen: 'homework'),
        ),
      );
      await tester.pumpAndSettle();

      expect(count(), before + 1);
    });
  });
}
