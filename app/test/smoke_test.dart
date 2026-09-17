import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/auth_controller.dart';
import 'package:academy_app/core/auth/auth_repository.dart';
import 'package:academy_app/core/auth/models/login_response.dart';
import 'package:academy_app/core/auth/models/me_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/storage/cookie_store.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/core/storage/token_store.dart';
import 'package:academy_app/features/auth/password_change_page.dart';
import 'package:academy_app/features/auth/teacher_notice_page.dart';
import 'package:academy_app/features/parent/parent_shell.dart';
import 'package:academy_app/features/student/student_shell.dart';
import 'package:academy_app/main.dart';

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
  Future<void> signup({
    required String code,
    String? name,
    required String phone,
    String? parentPhone,
  }) => throw UnimplementedError('스모크 테스트는 가입을 거치지 않는다');
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
  await tester.pumpWidget(AcademyApp(auth: auth));
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

    await tester.pumpWidget(AcademyApp(auth: auth));
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  test('Android 에뮬레이터 기본 baseUrl은 10.0.2.2다', () {
    // localhost 를 쓰면 에뮬레이터 자기 자신을 가리켜 조용히 실패한다.
    // 이 테스트는 호스트에서 돌아 Platform.isAndroid 가 false 이므로
    // 값이 아니라 함수가 존재하고 빈 문자열을 주지 않는 것만 확인한다.
    expect(resolveBaseUrl(), isNotEmpty);
    expect(resolveBaseUrl(), startsWith('http'));
  });

  // 역할 → 화면 배선. StudentShell·ParentShell 은 이 파일 전까지 테스트가
  // 하나도 건드리지 않았다 — 라우트 테이블이 main.dart 안에 있어서
  // redirectFor(순수 함수)만으로는 이 배선을 검증할 수 없었다. 학생·학부모
  // 빌더를 바꿔치기해도 78개 테스트가 전부 그린이었던 이유다. 학부모가
  // 학생 화면에 앉는 사고가 여기 걸린다.
  group('역할이 맞는 화면으로 연결된다', () {
    testWidgets('ready + student → StudentShell', (tester) async {
      await _pumpAtRole(tester, role: UserRole.student);

      expect(find.byType(StudentShell), findsOneWidget);
      expect(find.byType(ParentShell), findsNothing);
      expect(find.byType(TeacherNoticePage), findsNothing);
    });

    testWidgets('ready + parent → ParentShell', (tester) async {
      await _pumpAtRole(tester, role: UserRole.parent);

      expect(find.byType(ParentShell), findsOneWidget);
      expect(find.byType(StudentShell), findsNothing);
    });

    testWidgets('ready + teacher → TeacherNoticePage', (tester) async {
      await _pumpAtRole(tester, role: UserRole.teacher);

      expect(find.byType(TeacherNoticePage), findsOneWidget);
      expect(find.byType(StudentShell), findsNothing);
      expect(find.byType(ParentShell), findsNothing);
    });

    testWidgets('mustChangePassword → PasswordChangePage', (tester) async {
      await _pumpAtRole(
        tester,
        role: UserRole.student,
        mustChangePassword: true,
      );

      expect(find.byType(PasswordChangePage), findsOneWidget);
      expect(find.byType(StudentShell), findsNothing);
    });
  });
}
