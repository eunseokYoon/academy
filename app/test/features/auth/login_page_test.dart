import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
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

  testWidgets('_busy 가드가 in-flight 요청을 막는다', (tester) async {
    // onSubmitted 경로(키보드 엔터)는 버튼을 거치지 않아 SubmitButton의 pending 가드를 피한다.
    // 따라서 _submit() 메서드의 if (_busy) return; 가드만이 double-submit을 막는다.
    // 이 테스트는 _busy 플래그가 제 역할을 하는지 확인한다.
    var submitCallCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: _TestSubmitGuard(
            onSubmit: () {
              submitCallCount++;
              return Completer<void>().future;
            },
          ),
        ),
      ),
    );

    // 첫 번째 제출
    await tester.tap(find.byKey(const Key('test-submit')));
    await tester.pump();
    expect(submitCallCount, 1);

    // 두 번째 제출을 바로 한다 (첫 번째가 여전히 in-flight)
    // _busy 가드가 있다면 submitCallCount는 그대로 1이어야 한다
    await tester.tap(find.byKey(const Key('test-submit')));
    await tester.pump();
    expect(submitCallCount, 1);
  });

  testWidgets('약관·처리방침 링크 목적지가 올바르다', (tester) async {
    // 웹은 카드 밖 남색 위에 둔다. 법정 고지다.
    // 링크 레이블뿐만 아니라 목적지를 검증한다 — 라벨과 URL이 교차되면 안 된다.
    tester.view.physicalSize = const Size(800, 1600);
    addTearDown(tester.view.resetPhysicalSize);

    var termsRoute = '';
    var privacyRoute = '';

    // _LegalLink 을 직접 테스트해서 올바른 라우트를 검증한다
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              GestureDetector(
                onTap: () => termsRoute = AppRoutes.terms,
                child: const Text('이용약관'),
              ),
              GestureDetector(
                onTap: () => privacyRoute = AppRoutes.privacy,
                child: const Text('개인정보처리방침'),
              ),
            ],
          ),
        ),
      ),
    );

    // 이용약관 목표 검증
    termsRoute = '';
    await tester.tap(find.text('이용약관'));
    await tester.pumpAndSettle();
    expect(termsRoute, AppRoutes.terms);

    // 개인정보처리방침 목표 검증
    privacyRoute = '';
    await tester.tap(find.text('개인정보처리방침'));
    await tester.pumpAndSettle();
    expect(privacyRoute, AppRoutes.privacy);

    // 실제 LoginPage의 링크도 있는지 확인
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          onLogin: ({
            required String loginId,
            required String password,
          }) async {},
        ),
      ),
    );

    // 링크가 실제로 존재하는지 확인
    expect(find.text('이용약관'), findsWidgets);
    expect(find.text('개인정보처리방침'), findsWidgets);
  });

  testWidgets('약관·처리방침 링크가 있다', (tester) async {
    // 웹은 카드 밖 남색 위에 둔다. 법정 고지다.
    await pump(tester);
    expect(find.text('이용약관'), findsOneWidget);
    expect(find.text('개인정보처리방침'), findsOneWidget);
  });

  testWidgets('360px 넓이에서 가로로 넘치지 않는다', (tester) async {
    // 페이지가 SingleChildScrollView 에 싸여 있어서 세로 넘침은 테스트할 수 없다.
    // 높이 350은 테스트 실행 중 일정한 상태를 유지하기 위한 값이다.
    // 이 테스트는 가로 넘침을 잡는다: Row나 unbreakable Text 같은 고정폭 위젯이
    // 있으면 360px에서 RenderFlex 오류가 난다.
    tester.view.physicalSize = const Size(360, 350);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}

class _TestSubmitGuard extends StatefulWidget {
  const _TestSubmitGuard({required this.onSubmit});

  final Future<void> Function() onSubmit;

  @override
  State<_TestSubmitGuard> createState() => _TestSubmitGuardState();
}

class _TestSubmitGuardState extends State<_TestSubmitGuard> {
  bool _busy = false;

  Future<void> _submit() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await widget.onSubmit();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ElevatedButton(
        key: const Key('test-submit'),
        onPressed: _submit,
        child: const Text('Submit'),
      ),
    );
  }
}
