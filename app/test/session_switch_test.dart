import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/login_response.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';
import 'package:academy_app/features/auth/login_page.dart';
import 'package:academy_app/main.dart';

/// 같은 폰으로 **다른 사람이 로그인**하는 경로. 형제·자매가 폰 하나를
/// 나눠 쓰는 것은 이 학원에서 흔하다.
///
/// 홈 컨트롤러·자녀 선택은 한 사람의 세션(`AcademyApp` 의 `_Session`) 동안
/// 살아 있다. 로그아웃에서 안 버리면 다음 사람에게 앞 사람의 홈이 보인다 —
/// 서버의 접근 가드는 새 요청을 막을 뿐 이미 받은 데이터는 못 지운다.
/// `AcademyApp` 이 인증 상태를 듣고 세션을 dispose·새로 만드는지를 **실제
/// 앱·라우터로** 잠근다.

/// 로그인할 때마다 [next] 사용자가 된다.
class _SwitchAuthRepo implements AuthRepository {
  _SwitchAuthRepo(this.next);

  UserSummary next;

  @override
  Future<MeResponse> me() async => MeResponse(
    id: next.id,
    name: next.name,
    role: next.role,
    phone: '0101234567${next.id}',
    mustChangePassword: false,
  );

  @override
  Future<LoginResponse> login({
    required String loginId,
    required String password,
  }) async => LoginResponse(accessToken: 'at-${next.id}', user: next);

  @override

  Future<void> deleteAccount(String password) async {}


  @override
  Future<void> logout() async {}

  @override
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) => throw UnimplementedError();

  @override
  Future<SignupResponse> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) => throw UnimplementedError();
}

class _Reply {
  _Reply(this.body, {this.status = 200, this.gate});

  final Map<String, dynamic> body;
  final int status;

  /// 주면 이것이 끝날 때까지 응답을 붙잡는다 — 「목록이 오는 사이」를
  /// 화면에 멈춰 세워 보기 위해서다.
  final Completer<void>? gate;
}

/// 경로로 답한다. **대본에 없는 경로는 던진다** — 앞 사람 경로를 지운 뒤
/// 그 아이를 부르면 서버의 403 처럼 실패한다.
class _RoutingAdapter implements HttpClientAdapter {
  final routes = <String, _Reply>{};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final reply = routes[options.path];
    if (reply == null) {
      throw StateError('대본에 없는 요청이다: ${options.path}');
    }
    await reply.gate?.future;
    return ResponseBody.fromString(
      jsonEncode(reply.body),
      reply.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> _ok(Object? data) => {
  'success': true,
  'data': data,
  'error': null,
};

Map<String, dynamic> _parentHome(String name) => _ok({
  'student': {'name': name},
  'nextExam': null,
  'nextLessonDate': '2026-09-21',
  'nextLessonTime': '19:00',
  'nextLessonDDay': 2,
  'notices': {'totalCount': 0, 'recent': []},
  'pendingHomeworkCount': 1,
  'nextClinic': null,
  'thisMonthAttendance': {
    'present': 3,
    'late': 0,
    'absent': 0,
    'sick': 0,
    'excused': 0,
    'makeup': 0,
  },
});

Map<String, dynamic> _studentHome(String name) => _ok({
  'student': {'name': name},
  'nextLesson': null,
  'nextExam': null,
  'nextClinic': null,
  'currentHomeworks': [],
  'lastLesson': null,
  'notices': {'totalCount': 0, 'recent': []},
});

const _parentA = UserSummary(
  id: 10,
  name: '학부모A',
  role: UserRole.parent,
  mustChangePassword: false,
);
const _parentB = UserSummary(
  id: 20,
  name: '학부모B',
  role: UserRole.parent,
  mustChangePassword: false,
);

/// 앞 사람으로 로그인한 앱을 띄운다.
Future<(AuthController, _SwitchAuthRepo, _RoutingAdapter)> _boot(
  WidgetTester tester,
  UserSummary first,
  Map<String, _Reply> routes,
) async {
  final kv = InMemoryKeyValueStore();
  final tokens = TokenStore(kv);
  await tokens.write('at-${first.id}');
  final repo = _SwitchAuthRepo(first);
  final auth = AuthController(
    repository: repo,
    tokens: tokens,
    cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
    jar: CookieJar(),
  );
  final adapter = _RoutingAdapter()..routes.addAll(routes);
  final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
    ..httpClientAdapter = adapter;
  await auth.bootstrap();
  await tester.pumpWidget(AcademyApp(auth: auth, dio: dio, store: kv));
  await tester.pumpAndSettle();
  return (auth, repo, adapter);
}

/// 로그아웃 → 다음 사람으로 로그인. 60초 규칙 안이다(시계를 안 돌린다).
Future<void> _switchTo(
  WidgetTester tester,
  AuthController auth,
  _SwitchAuthRepo repo,
  UserSummary next,
) async {
  await auth.logout();
  await tester.pumpAndSettle();
  expect(find.byType(LoginPage), findsOneWidget);
  repo.next = next;
  await auth.login(loginId: 'x', password: 'y');
}

/// 로더가 도는 동안은 pumpAndSettle 이 끝나지 않는다 — 프레임을 몇 장만 민다.
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 5; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  testWidgets('학부모 B 로 바꿔 로그인하면 A 의 아이가 어디에도 없다', (tester) async {
    final (auth, repo, adapter) = await _boot(tester, _parentA, {
      '/api/parent/children': _Reply(
        _ok([
          {'studentId': 1, 'name': '김하늘'},
        ]),
      ),
      '/api/parent/children/1/home': _Reply(_parentHome('김하늘')),
    });
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);

    // B 의 서버: A 의 아이 경로는 이제 없다(부르면 403 처럼 실패한다).
    // 목록은 붙잡아 둔다 — **목록이 오는 사이**가 새는 순간이다.
    final gate = Completer<void>();
    adapter.routes
      ..clear()
      ..addAll({
        '/api/parent/children': _Reply(
          _ok([
            {'studentId': 2, 'name': '박바다'},
          ]),
          gate: gate,
        ),
        '/api/parent/children/2/home': _Reply(_parentHome('박바다')),
      });
    await _switchTo(tester, auth, repo, _parentB);
    await _pumpFrames(tester);

    expect(find.textContaining('김하늘'), findsNothing);

    gate.complete();
    await tester.pumpAndSettle();
    expect(find.text('박바다 학생\n학부모님, 환영합니다'), findsOneWidget);
    expect(find.textContaining('김하늘'), findsNothing);
  });

  testWidgets('B 의 목록이 실패하면 오류·다시 시도가 뜨고, 누르면 B 의 홈이다', (tester) async {
    final (auth, repo, adapter) = await _boot(tester, _parentA, {
      '/api/parent/children': _Reply(
        _ok([
          {'studentId': 1, 'name': '김하늘'},
        ]),
      ),
      '/api/parent/children/1/home': _Reply(_parentHome('김하늘')),
    });
    expect(find.text('김하늘 학생\n학부모님, 환영합니다'), findsOneWidget);

    adapter.routes
      ..clear()
      ..addAll({
        '/api/parent/children': _Reply({
          'success': false,
          'data': null,
          'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
        }, status: 500),
      });
    await _switchTo(tester, auth, repo, _parentB);
    await tester.pumpAndSettle();

    expect(find.text('서버 오류입니다.'), findsOneWidget);
    expect(find.text('다시 시도'), findsOneWidget);
    expect(find.textContaining('김하늘'), findsNothing);

    // 다시 시도 — 이번엔 성공한다.
    adapter.routes
      ..clear()
      ..addAll({
        '/api/parent/children': _Reply(
          _ok([
            {'studentId': 2, 'name': '박바다'},
          ]),
        ),
        '/api/parent/children/2/home': _Reply(_parentHome('박바다')),
      });
    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.text('박바다 학생\n학부모님, 환영합니다'), findsOneWidget);
    expect(find.textContaining('김하늘'), findsNothing);
  });

  testWidgets('학생 B 로 바꿔 로그인하면 60초 안이어도 A 의 홈이 없다', (tester) async {
    const studentA = UserSummary(
      id: 1,
      name: '김하늘',
      role: UserRole.student,
      mustChangePassword: false,
    );
    const studentB = UserSummary(
      id: 2,
      name: '박바다',
      role: UserRole.student,
      mustChangePassword: false,
    );
    final (auth, repo, adapter) = await _boot(tester, studentA, {
      '/api/student/home': _Reply(_studentHome('김하늘')),
    });
    expect(find.textContaining('김하늘'), findsWidgets);

    adapter.routes['/api/student/home'] = _Reply(_studentHome('박바다'));
    await _switchTo(tester, auth, repo, studentB);
    await tester.pumpAndSettle();

    // 그려진 화면인지 먼저 본다.
    expect(find.textContaining('박바다'), findsWidgets);
    expect(find.textContaining('김하늘'), findsNothing);
  });

  testWidgets('로그아웃 전에 나간 A 의 요청이 B 로그인 뒤 도착해도 B 에게 안 보인다', (tester) async {
    // A 의 홈 요청이 느린 망에 걸린 채로 로그아웃한다. 세션을 버리지 않고
    // 이어 쓰면 B 의 홈이 그 진행 중 요청에 합류해 A 의 응답을 그린다.
    const studentA = UserSummary(
      id: 1,
      name: '김하늘',
      role: UserRole.student,
      mustChangePassword: false,
    );
    const studentB = UserSummary(
      id: 2,
      name: '박바다',
      role: UserRole.student,
      mustChangePassword: false,
    );
    final kv = InMemoryKeyValueStore();
    final tokens = TokenStore(kv);
    await tokens.write('at-1');
    final repo = _SwitchAuthRepo(studentA);
    final auth = AuthController(
      repository: repo,
      tokens: tokens,
      cookies: CookieStore(kv, Uri.parse('https://example.test/api/auth')),
      jar: CookieJar(),
    );
    final slowA = Completer<void>();
    final adapter = _RoutingAdapter()
      ..routes['/api/student/home'] = _Reply(_studentHome('김하늘'), gate: slowA);
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = adapter;
    await auth.bootstrap();
    await tester.pumpWidget(AcademyApp(auth: auth, dio: dio, store: kv));
    await _pumpFrames(tester); // A 의 홈은 로딩 중이다.
    expect(find.textContaining('김하늘'), findsNothing);

    adapter.routes['/api/student/home'] = _Reply(_studentHome('박바다'));
    await _switchTo(tester, auth, repo, studentB);
    await _pumpFrames(tester);

    slowA.complete(); // A 의 응답이 이제야 도착한다.
    await tester.pumpAndSettle();

    expect(find.textContaining('박바다'), findsWidgets);
    expect(find.textContaining('김하늘'), findsNothing);
  });
}
