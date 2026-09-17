import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/auth/password_change_page.dart';

void main() {
  late List<(String, String)> changes;
  late int logouts;

  Future<void> pump(WidgetTester tester, {Object? error}) async {
    changes = [];
    logouts = 0;
    await tester.pumpWidget(MaterialApp(
      home: PasswordChangePage(
        onChange: ({
          required String currentPassword,
          required String newPassword,
        }) async {
          changes.add((currentPassword, newPassword));
          if (error != null) throw error;
        },
        onLogout: () async => logouts++,
      ),
    ));
  }

  Future<void> fill(
    WidgetTester tester, {
    String current = '0000',
    String next = 'newpass1',
    String? confirm,
  }) async {
    await tester.enterText(find.byKey(const Key('pw-current')), current);
    await tester.enterText(find.byKey(const Key('pw-new')), next);
    await tester.enterText(find.byKey(const Key('pw-confirm')), confirm ?? next);
  }

  testWidgets('현재·새 비밀번호로 변경을 부른다', (tester) async {
    await pump(tester);
    await fill(tester);
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();

    expect(changes.single, ('0000', 'newpass1'));
  });

  testWidgets('8자 미만이면 막는다', (tester) async {
    // 정책은 8자 이상뿐이다. 특수문자 강제 같은 제약을 넣지 마라 —
    // 학부모 연령대를 고려한 확정 사항이다.
    await pump(tester);
    await fill(tester, next: 'short7');
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();

    expect(changes, isEmpty);
    expect(find.text('새 비밀번호는 8자 이상이어야 합니다.'), findsOneWidget);
  });

  testWidgets('확인이 다르면 막는다', (tester) async {
    await pump(tester);
    await fill(tester, next: 'newpass1', confirm: 'newpass2');
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();

    expect(changes, isEmpty);
    expect(find.text('새 비밀번호가 서로 다릅니다.'), findsOneWidget);
  });

  testWidgets('현재 비밀번호와 같으면 막는다', (tester) async {
    // 0000 을 그대로 넣으면 서버는 통과시키지만 아무것도 안 바뀐다.
    await pump(tester);
    await fill(tester, current: 'samepass1', next: 'samepass1');
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();

    expect(changes, isEmpty);
    expect(find.text('지금 비밀번호와 다른 값을 넣어 주세요.'), findsOneWidget);
  });

  testWidgets('서버 문구를 그대로 보여준다', (tester) async {
    await pump(tester,
        error: const ApiException(
          code: 'INVALID_CREDENTIALS',
          message: '아이디 또는 비밀번호가 올바르지 않습니다.',
        ));
    await fill(tester);
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pumpAndSettle();

    expect(find.text('아이디 또는 비밀번호가 올바르지 않습니다.'), findsOneWidget);
  });

  testWidgets('로그아웃으로 나갈 수 있다', (tester) async {
    // 라우터가 이 화면만 허용하므로, 비밀번호를 모르는 사용자에게
    // 로그아웃 말고는 나갈 길이 없다. 막다른 길을 만들지 마라.
    await pump(tester);
    await tester.tap(find.byKey(const Key('pw-logout')));
    await tester.pump();

    expect(logouts, 1);
  });

  testWidgets('360px에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });
}
