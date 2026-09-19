import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/auth/password_change_page.dart';

void main() {
  late List<(String, String)> changes;
  late int logouts;

  Future<void> pump(
    WidgetTester tester, {
    Object? error,
    bool hold = false,
  }) async {
    changes = [];
    logouts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: PasswordChangePage(
          onChange:
              ({
                required String currentPassword,
                required String newPassword,
              }) async {
                changes.add((currentPassword, newPassword));
                if (error != null) throw error;
                if (hold) return Completer<void>().future;
              },
          onLogout: () async => logouts++,
        ),
      ),
    );
  }

  Future<void> fill(
    WidgetTester tester, {
    String current = '0000',
    String next = 'newpass1',
    String? confirm,
  }) async {
    await tester.enterText(find.byKey(const Key('pw-current')), current);
    await tester.enterText(find.byKey(const Key('pw-new')), next);
    await tester.enterText(
      find.byKey(const Key('pw-confirm')),
      confirm ?? next,
    );
  }

  testWidgets('제목과 초기 비밀번호 안내가 있다', (tester) async {
    await pump(tester);
    expect(find.text('비밀번호 변경'), findsWidgets);
    expect(find.textContaining('0000'), findsOneWidget);
  });

  testWidgets('세 칸에 라벨이 있다', (tester) async {
    await pump(tester);
    expect(find.text('현재 비밀번호'), findsOneWidget);
    expect(find.text('새 비밀번호'), findsOneWidget);
    expect(find.text('새 비밀번호 확인'), findsOneWidget);
  });

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
    await fill(tester, next: 'short77');
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
    await pump(
      tester,
      error: const ApiException(
        code: 'INVALID_CREDENTIALS',
        message: '아이디 또는 비밀번호가 올바르지 않습니다.',
      ),
    );
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

  testWidgets('보내는 동안 버튼을 두 번 누를 수 없다', (tester) async {
    // 더블탭으로 변경이 두 번 나가면 서버가 리프레시 토큰을 두 번 폐기하려 든다.
    // hold: true 로 첫 호출을 붙잡아 두고 두 번째 탭을 시도한다.
    await pump(tester, hold: true);
    await fill(tester);
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('pw-submit')));
    await tester.pump();

    expect(changes.length, 1);
  });

  testWidgets('변경 후 재로그인 안내가 있다', (tester) async {
    await pump(tester);
    expect(
      find.text('변경하면 다른 기기의 로그인이 모두 해제됩니다. 새 비밀번호로 다시 로그인해 주세요.'),
      findsOneWidget,
    );
  });

  testWidgets('360px 짧은 화면에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 420);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
