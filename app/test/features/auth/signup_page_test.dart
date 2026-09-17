import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/auth/signup_page.dart';

void main() {
  late List<Map<String, String>> calls;

  Future<void> pump(WidgetTester tester, {Object? error}) async {
    calls = [];
    await tester.pumpWidget(MaterialApp(
      home: SignupPage(
        onSignup: ({
          required String code,
          required String name,
          required String phone,
          required String parentPhone,
        }) async {
          calls.add({
            'code': code,
            'name': name,
            'phone': phone,
            'parentPhone': parentPhone,
          });
          if (error != null) throw error;
        },
      ),
    ));
  }

  Future<void> fillAll(WidgetTester tester) async {
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(find.byKey(const Key('signup-phone')), '010-1234-5678');
    await tester.enterText(
        find.byKey(const Key('signup-parent-phone')), '010-9876-5432');
  }

  testWidgets('네 칸을 채우면 가입을 부른다', (tester) async {
    await pump(tester);
    await fillAll(tester);
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls.single, {
      'code': 'ABCD12',
      'name': '김하늘',
      'phone': '01012345678',
      'parentPhone': '01098765432',
    });
  });

  testWidgets('비밀번호 칸이 없다', (tester) async {
    // 가입은 비밀번호를 받지 않는다. phone 이 로그인 아이디가 되고
    // 초기 비밀번호는 0000 이다. 칸을 만들면 서버가 무시한다.
    await pump(tester);
    expect(find.byKey(const Key('signup-password')), findsNothing);
  });

  testWidgets('역할을 고르는 칸이 없다', (tester) async {
    // 서버가 code 를 보고 학생·학부모를 판별한다.
    await pump(tester);
    expect(find.text('학생'), findsNothing);
    expect(find.text('학부모'), findsNothing);
  });

  testWidgets('본인 번호와 보호자 번호가 같으면 막는다', (tester) async {
    // users.login_id 가 UNIQUE 라서 서버가 409 로 막지만, 여기서 먼저 막으면
    // 학생이 무엇을 잘못했는지 안다.
    await pump(tester);
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(find.byKey(const Key('signup-phone')), '01012345678');
    await tester.enterText(
        find.byKey(const Key('signup-parent-phone')), '01012345678');
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls, isEmpty);
    expect(find.text('본인 번호와 보호자 번호가 같을 수 없습니다.'), findsOneWidget);
  });

  testWidgets('빈 칸이 있으면 서버를 부르지 않는다', (tester) async {
    await pump(tester);
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls, isEmpty);
    expect(find.text('반 코드를 입력해 주세요.'), findsOneWidget);
  });

  testWidgets('코드가 틀리면 서버 문구를 보여준다', (tester) async {
    await pump(tester,
        error: const ApiException(
          code: 'INVITE_CODE_INVALID',
          message: '초대코드가 유효하지 않습니다.',
        ));
    await fillAll(tester);
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pumpAndSettle();

    expect(find.text('초대코드가 유효하지 않습니다.'), findsOneWidget);
  });

  testWidgets('성공하면 초기 비밀번호를 알려준다', (tester) async {
    // 가입은 로그인이 아니다. 계정만 만들어지므로 0000 으로 로그인해야 한다.
    await pump(tester);
    await fillAll(tester);
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pumpAndSettle();

    expect(find.textContaining('0000'), findsOneWidget);
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
