import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/auth/models/signup_response.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/features/auth/signup_page.dart';

void main() {
  late List<Map<String, String?>> calls;

  // 백엔드 SignupResponse.java(record)를 따른다 — studentId는 없고
  // role·initialPassword가 있다(보고서 Step 1 참고).
  SignupResponse fixture({String? classRoomName = 'A고 2학년 목요일반'}) =>
      SignupResponse(
        role: UserRole.student,
        loginId: '01012345678',
        studentName: '김하늘',
        classRoomName: classRoomName,
        initialPassword: '0000',
      );

  Future<void> pump(
    WidgetTester tester, {
    Object? error,
    bool hold = false,
    SignupResponse? result,
  }) async {
    calls = [];
    await tester.pumpWidget(
      MaterialApp(
        home: SignupPage(
          onSignup:
              ({
                required String code,
                String? name,
                required String phone,
                String? parentPhone,
              }) async {
                calls.add({
                  'code': code,
                  'name': name,
                  'phone': phone,
                  'parentPhone': parentPhone,
                });
                if (error != null) throw error;
                if (hold) return Completer<SignupResponse>().future;
                return result ?? fixture();
              },
        ),
      ),
    );
  }

  testWidgets('기본은 반 코드 모드이고 네 칸이다', (tester) async {
    await pump(tester);
    expect(find.text('반 코드'), findsOneWidget);
    expect(find.text('이름'), findsOneWidget);
    expect(find.text('내 전화번호'), findsOneWidget);
    expect(find.text('보호자 번호'), findsOneWidget);
  });

  testWidgets('비밀번호 칸도 역할 선택도 없다', (tester) async {
    // phone 이 로그인 아이디가 되고 초기 비밀번호는 0000 이다.
    // 역할은 서버가 code 로 판별한다.
    await pump(tester);
    expect(find.text('비밀번호'), findsNothing);
    expect(find.byType(DropdownButton<String>), findsNothing);
    expect(find.byType(Radio<String>), findsNothing);
  });

  testWidgets('개인 코드 모드로 전환하면 두 칸이 된다', (tester) async {
    // 선생님이 직접 등록한 학생의 경로다. 이 모드가 없으면 그 학생들은
    // 앱으로 가입 자체를 할 수 없다.
    await pump(tester);
    // 폼이 기본 테스트 창(800x600)보다 길어 이 링크가 화면 아래로 밀릴 수
    // 있다 — 스크롤 가능한 화면이므로 먼저 보이는 곳으로 스크롤한다.
    await tester.ensureVisible(find.byKey(const Key('signup-to-personal')));
    await tester.tap(find.byKey(const Key('signup-to-personal')));
    await tester.pumpAndSettle();

    expect(find.text('코드'), findsOneWidget);
    expect(find.text('전화번호'), findsOneWidget);
    expect(find.text('이름'), findsNothing);
    expect(find.text('보호자 번호'), findsNothing);
    expect(find.text('코드를 받으실 때 선생님께 알려 주신 번호여야 합니다.'), findsOneWidget);
  });

  testWidgets('개인 코드 모드는 이름과 보호자 번호를 보내지 않는다', (tester) async {
    await pump(tester);
    // 폼이 기본 테스트 창(800x600)보다 길어 이 링크가 화면 아래로 밀릴 수
    // 있다 — 스크롤 가능한 화면이므로 먼저 보이는 곳으로 스크롤한다.
    await tester.ensureVisible(find.byKey(const Key('signup-to-personal')));
    await tester.tap(find.byKey(const Key('signup-to-personal')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('signup-code')), 'k7f2qx');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '010-5555-6666',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls.single['name'], isNull);
    expect(calls.single['parentPhone'], isNull);
  });

  testWidgets('개인 코드 모드도 전화번호는 숫자만 보낸다', (tester) async {
    await pump(tester);
    // 폼이 기본 테스트 창(800x600)보다 길어 이 링크가 화면 아래로 밀릴 수
    // 있다 — 스크롤 가능한 화면이므로 먼저 보이는 곳으로 스크롤한다.
    await tester.ensureVisible(find.byKey(const Key('signup-to-personal')));
    await tester.tap(find.byKey(const Key('signup-to-personal')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byKey(const Key('signup-code')), 'K7F2QX');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '010-5555-6666',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls.single['phone'], '01055556666');
  });

  testWidgets('코드를 대문자로 보낸다', (tester) async {
    // 웹은 code.trim().toUpperCase() 로 보낸다.
    await pump(tester);
    await tester.enterText(find.byKey(const Key('signup-code')), ' abcd12 ');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '010-1234-5678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '010-9876-5432',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls.single['code'], 'ABCD12');
    expect(calls.single['phone'], '01012345678');
    expect(calls.single['parentPhone'], '01098765432');
  });

  testWidgets('본인 번호와 보호자 번호가 같으면 막는다', (tester) async {
    // users.login_id 가 UNIQUE 라서 서버도 409 로 막지만,
    // 여기서 먼저 막으면 학생이 무엇을 잘못했는지 안다.
    await pump(tester);
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01012345678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '01012345678',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls, isEmpty);
    expect(find.text('본인 번호와 보호자 번호가 같을 수 없습니다.'), findsOneWidget);
  });

  testWidgets('성공 화면이 이름·반·아이디를 보여준다', (tester) async {
    // 응답을 버리면 엉뚱한 반 코드로 가입한 것을 알아차릴 방법이 없다.
    await pump(tester);
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01012345678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '01098765432',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pumpAndSettle();

    expect(find.text('가입이 완료되었습니다'), findsOneWidget);
    expect(find.text('김하늘'), findsWidgets);
    expect(find.text('A고 2학년 목요일반'), findsOneWidget);
    expect(find.text('01012345678'), findsWidgets);
    expect(find.textContaining('0000'), findsOneWidget);
  });

  testWidgets('반 이름이 없으면 그 줄을 그리지 않는다', (tester) async {
    await pump(tester, result: fixture(classRoomName: null));
    // 폼이 기본 테스트 창(800x600)보다 길어 이 링크가 화면 아래로 밀릴 수
    // 있다 — 스크롤 가능한 화면이므로 먼저 보이는 곳으로 스크롤한다.
    await tester.ensureVisible(find.byKey(const Key('signup-to-personal')));
    await tester.tap(find.byKey(const Key('signup-to-personal')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('signup-code')), 'K7F2QX');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01055556666',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pumpAndSettle();

    expect(find.text('반'), findsNothing);
  });

  testWidgets('빈 칸이 있으면 서버를 부르지 않는다', (tester) async {
    await pump(tester);
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();
    expect(calls, isEmpty);
    expect(find.text('반 코드를 입력해 주세요.'), findsOneWidget);
  });

  testWidgets('서버 문구를 그대로 보여준다', (tester) async {
    await pump(
      tester,
      error: const ApiException(
        code: 'INVITE_CODE_INVALID',
        message: '초대코드가 유효하지 않습니다.',
      ),
    );
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01012345678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '01098765432',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pumpAndSettle();

    expect(find.text('초대코드가 유효하지 않습니다.'), findsOneWidget);
  });

  testWidgets('보내는 동안 버튼을 두 번 누를 수 없다', (tester) async {
    await pump(tester, hold: true);
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01012345678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '01098765432',
    );
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('signup-submit')));
    await tester.pump();

    expect(calls.length, 1);
  });

  testWidgets('보내는 동안 키보드 완료로도 두 번 제출할 수 없다', (tester) async {
    // SubmitButton은 자기 자신에 대한 두 번째 탭만 막는다. 마지막 칸의
    // onSubmitted 가 _submit 을 직접 부르므로 그 경로는 버튼을 거치지 않는다 —
    // _submit 자신의 _busy 가드가 없으면 이 경로로 두 번 나간다.
    //
    // tester.testTextInput.receiveAction(TextInputAction.done) 은 done
    // 액션의 기본 동작(포커스 해제 → 연결 재시작)까지 함께 트리거해서 두
    // 번째 호출이 같은 입력 클라이언트에 닿지 않았다 — 첫 호출만으로도
    // _busy 유무와 상관없이 늘 호출 1회로 끝나 가드를 판별하지 못했다.
    // 그래서 내부 TextField 의 onSubmitted 콜백을 직접 두 번 호출해
    // 프레임워크의 포커스 해제 부작용 없이 _submit() 재진입만을 본다.
    await pump(tester, hold: true);
    await tester.enterText(find.byKey(const Key('signup-code')), 'ABCD12');
    await tester.enterText(find.byKey(const Key('signup-name')), '김하늘');
    await tester.enterText(
      find.byKey(const Key('signup-phone')),
      '01012345678',
    );
    await tester.enterText(
      find.byKey(const Key('signup-parent-phone')),
      '01098765432',
    );

    final parentPhoneField = tester.widget<TextField>(
      find.descendant(
        of: find.byKey(const Key('signup-parent-phone')),
        matching: find.byType(TextField),
      ),
    );

    parentPhoneField.onSubmitted!('01098765432');
    await tester.pump();
    parentPhoneField.onSubmitted!('01098765432');
    await tester.pump();

    expect(calls.length, 1);
  });

  testWidgets('360px 짧은 화면에서 넘치지 않는다', (tester) async {
    // 네 칸이라 로그인보다 길다. 높이는 Step 10 에서 실측해 정해라.
    tester.view.physicalSize = const Size(360, 420);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await pump(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
