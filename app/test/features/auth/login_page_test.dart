import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/router/app_router.dart';
import 'package:academy_app/features/auth/login_page.dart';
import 'package:academy_app/shared/branding.dart';
import 'package:academy_app/shared/widgets/logo.dart';

void main() {
  late List<(String, String)> logins;

  Future<void> pump(
    WidgetTester tester, {
    Object? error,
    bool hold = false,
  }) async {
    logins = [];
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          onLogin: ({required String loginId, required String password}) async {
            logins.add((loginId, password));
            if (error != null) throw error;
            if (hold) return Completer<void>().future;
          },
        ),
      ),
    );
  }

  testWidgets('상호와 로고를 보여준다', (tester) async {
    // 앱이 「학원」을 띄우고 있었다. 실제 이름은 남지원영어LAB 이다.
    await pump(tester);
    expect(find.byType(LogoBadge), findsOneWidget);
    expect(find.textContaining(academyNameHead), findsOneWidget);
  });

  testWidgets('카드 안에 제목과 부제가 있다', (tester) async {
    await pump(tester);
    expect(find.text('로그인'), findsWidgets);
    expect(find.text('전화번호로 로그인합니다.'), findsOneWidget);
  });

  testWidgets('전화번호 입력에 하이픈이 자동으로 붙는다', (tester) async {
    // 웹과 같게. 보낼 때는 숫자만 간다.
    await pump(tester);
    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.pump();
    expect(find.text('010-1234-5678'), findsWidgets);
  });

  testWidgets('보낼 때는 숫자만 보낸다', (tester) async {
    await pump(tester);
    await tester.enterText(find.byKey(const Key('login-id')), '010-1234-5678');
    await tester.enterText(find.byKey(const Key('login-password')), '0000');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    expect(logins.single, ('01012345678', '0000'));
  });

  testWidgets('빈 칸이면 서버를 부르지 않는다', (tester) async {
    await pump(tester);
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    expect(logins, isEmpty);
    expect(find.text('전화번호를 입력해 주세요.'), findsOneWidget);
  });

  testWidgets('서버 문구를 그대로 보여준다', (tester) async {
    // 백엔드 ErrorCode 의 message 가 이미 사용자 문구다. 감싸지 마라.
    await pump(
      tester,
      error: const ApiException(
        code: 'INVALID_CREDENTIALS',
        message: '아이디 또는 비밀번호가 올바르지 않습니다.',
      ),
    );
    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.enterText(find.byKey(const Key('login-password')), 'wrong');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();
    expect(find.text('아이디 또는 비밀번호가 올바르지 않습니다.'), findsOneWidget);
  });

  testWidgets('보내는 동안 버튼을 두 번 누를 수 없다', (tester) async {
    // 두 번 나가면 리프레시 토큰이 두 개 발급된다.
    await pump(tester, hold: true);
    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.enterText(find.byKey(const Key('login-password')), '0000');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    expect(logins.length, 1);
  });

  testWidgets('키보드 완료 키로 두 번 보내도 요청은 한 번만 나간다', (tester) async {
    // 버튼 경로는 SubmitButton의 pending 가드(onPressed: pending ? null :
    // onPressed)가 이미 이중 제출을 막아서 위 테스트는 _busy 가드 없이도
    // 통과한다. 비밀번호 필드의 onSubmitted(키보드 「완료」 키)는 버튼을
    // 거치지 않아 그 가드를 피해 간다 — 이 경로를 막는 건 _submit() 맨 앞의
    // if (_busy) return; 뿐이다.
    //
    // tester.testTextInput.receiveAction(TextInputAction.done) 은 done
    // 액션의 기본 동작(포커스 해제 → 연결 재시작)까지 함께 트리거해서 두
    // 번째 호출이 같은 입력 클라이언트에 닿지 않았다 — 첫 호출만으로도
    // _busy 유무와 상관없이 늘 호출 1회로 끝나 가드를 판별하지 못했다.
    // 그래서 내부 TextField 의 onSubmitted 콜백을 직접 두 번 호출해
    // 프레임워크의 포커스 해제 부작용 없이 _submit() 재진입만을 본다.
    await pump(tester, hold: true);
    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.enterText(find.byKey(const Key('login-password')), '0000');

    final passwordField = tester.widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('login-password')),
        matching: find.byType(TextField),
      ),
    );

    passwordField.onSubmitted!('0000');
    await tester.pump();
    passwordField.onSubmitted!('0000');
    await tester.pump();

    expect(logins.length, 1);
  });

  testWidgets('약관·처리방침 링크가 각자 올바른 경로로 이동한다', (tester) async {
    // 실제 GoRouter 로 탭해서 도착한 경로를 확인한다. 라벨 존재만 보면
    // context.go(AppRoutes.terms) 와 context.go(AppRoutes.privacy) 가
    // 서로 바뀌어도 잡지 못한다. 목적지 화면은 라우팅만 확인하면 되므로
    // 실제 약관·처리방침 화면 대신 식별 가능한 자리표시 텍스트를 쓴다.
    final router = GoRouter(
      initialLocation: AppRoutes.login,
      routes: [
        GoRoute(
          path: AppRoutes.login,
          builder: (context, state) => LoginPage(
            onLogin: ({
              required String loginId,
              required String password,
            }) async {},
          ),
        ),
        GoRoute(
          path: AppRoutes.terms,
          builder: (context, state) => const Scaffold(body: Text('약관 화면 도착')),
        ),
        GoRoute(
          path: AppRoutes.privacy,
          builder: (context, state) => const Scaffold(body: Text('처리방침 화면 도착')),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(MaterialApp.router(routerConfig: router));

    // 기본 테스트 뷰포트(800x600)에는 카드 아래 링크 줄이 다 안 들어와서
    // SingleChildScrollView 안으로 스크롤해야 링크가 hit-test 된다.
    await tester.ensureVisible(find.text('이용약관'));
    await tester.tap(find.text('이용약관'));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.terms,
    );
    expect(find.text('약관 화면 도착'), findsOneWidget);

    router.go(AppRoutes.login);
    await tester.pumpAndSettle();

    await tester.ensureVisible(find.text('개인정보처리방침'));
    await tester.tap(find.text('개인정보처리방침'));
    await tester.pumpAndSettle();
    expect(
      router.routerDelegate.currentConfiguration.uri.path,
      AppRoutes.privacy,
    );
    expect(find.text('처리방침 화면 도착'), findsOneWidget);
  });

  testWidgets('약관·처리방침 링크가 있다', (tester) async {
    // 웹은 카드 밖 남색 위에 둔다. 법정 고지다.
    await pump(tester);
    expect(find.text('이용약관'), findsOneWidget);
    expect(find.text('개인정보처리방침'), findsOneWidget);
  });

  testWidgets('360px 넓이에서 가로로 넘치지 않는다', (tester) async {
    // 페이지가 SingleChildScrollView 에 싸여 있어서 세로 넘침은 테스트할 수 없다.
    // 높이 350은 테스트 실행 중 일정한 상태를 유지하기 위한 값이고 실측한
    // 값은 아니다 — 이 테스트가 잡는 건 가로 넘침이다: Row나 unbreakable
    // Text 같은 고정폭 위젯이 있으면 360px 너비에서 RenderFlex 오류가 난다.
    tester.view.physicalSize = const Size(360, 350);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('비밀번호 필드는 글자를 가리고 전화번호 필드는 가리지 않는다', (tester) async {
    await pump(tester);

    // 전화번호 필드 확인 (가려지면 안 됨)
    final phoneField = tester.widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('login-id')),
        matching: find.byType(TextField),
      ),
    );
    expect(phoneField.obscureText, isFalse);

    // 비밀번호 필드 확인 (가려져야 함)
    final passwordField = tester.widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('login-password')),
        matching: find.byType(TextField),
      ),
    );
    expect(passwordField.obscureText, isTrue);
  });
}
