import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/auth/login_page.dart';

void main() {
  late List<(String, String)> logins;

  /// `hold`가 참이면 로그인이 끝나지 않는다 — 재탭 방어를 확인할 때 쓴다.
  Future<void> pump(
    WidgetTester tester, {
    Object? error,
    bool hold = false,
  }) async {
    logins = [];
    await tester.pumpWidget(MaterialApp(
      home: LoginPage(
        onLogin: ({required String loginId, required String password}) async {
          logins.add((loginId, password));
          if (error != null) throw error;
          if (hold) return Completer<void>().future;
        },
      ),
    ));
  }

  testWidgets('전화번호와 비밀번호로 로그인을 부른다', (tester) async {
    await pump(tester);

    await tester.enterText(find.byKey(const Key('login-id')), '010-1234-5678');
    await tester.enterText(find.byKey(const Key('login-password')), '0000');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();

    // 하이픈은 앱에서 지운다. 서버도 정규화하지만 보낼 값이 깔끔해야
    // 로그를 읽을 때 헷갈리지 않는다.
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
    // 백엔드 ErrorCode 의 message 가 이미 한국어 사용자 문구다.
    // 앱에서 문구를 다시 만들면 같은 실패가 화면마다 다르게 보인다.
    await pump(tester,
        error: const ApiException(
          code: 'INVALID_CREDENTIALS',
          message: '아이디 또는 비밀번호가 올바르지 않습니다.',
        ));

    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.enterText(find.byKey(const Key('login-password')), 'wrong');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pumpAndSettle();

    expect(find.text('아이디 또는 비밀번호가 올바르지 않습니다.'), findsOneWidget);
  });

  testWidgets('보내는 동안 버튼을 두 번 누를 수 없다', (tester) async {
    // 더블탭으로 로그인이 두 번 나가면 리프레시 토큰이 두 개 발급된다.
    // hold: true 로 첫 호출을 붙잡아 두고 두 번째 탭을 시도한다.
    await pump(tester, hold: true);

    await tester.enterText(find.byKey(const Key('login-id')), '01012345678');
    await tester.enterText(find.byKey(const Key('login-password')), '0000');
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('login-submit')));
    await tester.pump();

    expect(logins.length, 1);
  });

  testWidgets('360px에서 넘치지 않는다', (tester) async {
    // 높이를 350으로 잡는 이유가 있다. 640은 물론 420에서도 로그인 화면은
    // 칸이 둘뿐이라 내용이 다 들어가서 SingleChildScrollView를 지워도 오버플로가
    // 나지 않아 이 테스트가 통과한다 — 즉 아무것도 지키지 못한다. 350에서
    // 「A RenderFlex overflowed by 58 pixels on the bottom.」로 실제로 갈리는 걸
    // 확인했다. 다른 두 화면(칸이 더 많다)은 420에서도 갈린다.
    tester.view.physicalSize = const Size(360, 350);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });
}
