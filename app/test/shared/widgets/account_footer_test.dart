import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/shared/widgets/account_footer.dart';

/// 2026-10-03: 스토어가 요구하는 앱 안 계정 삭제. 로그아웃 줄에 함께 있다.
void main() {
  Future<void> pump(
    WidgetTester tester,
    Future<void> Function(String) onDelete,
  ) => tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: AccountFooter(
          logoutKey: const Key('logout'),
          onLogout: () async {},
          onDeleteAccount: onDelete,
        ),
      ),
    ),
  );

  testWidgets('계정 삭제는 비밀번호를 받아 넘기고 대화상자를 닫는다', (tester) async {
    final calls = <String>[];
    await pump(tester, (pw) async => calls.add(pw));
    expect(find.byKey(const Key('logout')), findsOneWidget);

    await tester.tap(find.byKey(const Key('account-delete')));
    await tester.pumpAndSettle();
    expect(find.textContaining('되돌릴 수 없습니다'), findsOneWidget);

    await tester.enterText(
      find.descendant(
        of: find.byKey(const Key('account-delete-password')),
        matching: find.byType(TextField),
      ),
      'pw-1234',
    );
    await tester.tap(find.byKey(const Key('account-delete-confirm')));
    await tester.pumpAndSettle();

    expect(calls, ['pw-1234']);
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets('비밀번호가 비어 있으면 보내지 않는다', (tester) async {
    final calls = <String>[];
    await pump(tester, (pw) async => calls.add(pw));
    await tester.tap(find.byKey(const Key('account-delete')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('account-delete-confirm')));
    await tester.pumpAndSettle();

    expect(calls, isEmpty);
    expect(find.byType(AlertDialog), findsOneWidget);
  });

  testWidgets('서버가 거절하면 그 문구를 띄우고 대화상자를 남긴다', (tester) async {
    await pump(
      tester,
      (_) async => throw const ApiException(
        code: 'INVALID_CREDENTIALS',
        message: '아이디 또는 비밀번호가 올바르지 않습니다.',
        statusCode: 401,
      ),
    );
    await tester.tap(find.byKey(const Key('account-delete')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.descendant(
        of: find.byKey(const Key('account-delete-password')),
        matching: find.byType(TextField),
      ),
      'wrong',
    );
    await tester.tap(find.byKey(const Key('account-delete-confirm')));
    await tester.pumpAndSettle();

    expect(find.text('아이디 또는 비밀번호가 올바르지 않습니다.'), findsOneWidget);
    expect(find.byType(AlertDialog), findsOneWidget);
  });

  testWidgets('360px 에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await pump(tester, (_) async {});
    expect(find.text('계정 삭제'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
