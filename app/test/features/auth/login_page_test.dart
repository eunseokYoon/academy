import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
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

  testWidgets('약관·처리방침 링크가 있다', (tester) async {
    // 웹은 카드 밖 남색 위에 둔다. 법정 고지다.
    await pump(tester);
    expect(find.text('이용약관'), findsOneWidget);
    expect(find.text('개인정보처리방침'), findsOneWidget);
  });

  testWidgets('360px 짧은 화면에서 넘치지 않는다', (tester) async {
    // 높이는 실측해서 정해라 — 640 은 내용이 다 들어가 판별력이 없다.
    // Step 6 에서 스크롤을 제거해 실패하는 높이를 찾아 이 값을 바꿔라.
    tester.view.physicalSize = const Size(360, 350);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
