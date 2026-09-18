import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/shared/widgets/form_error.dart';

void main() {
  testWidgets('message 가 null 이면 아무것도 그리지 않는다', (tester) async {
    // 오류가 없을 때 빈 상자가 자리를 차지하면 폼이 들썩인다.
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: FormError(message: null))),
    );
    expect(find.byType(Text), findsNothing);
  });

  testWidgets('서버 문구를 그대로 보여준다', (tester) async {
    // 백엔드 ErrorCode 의 message 가 이미 한국어 사용자 문구다.
    // 앱에서 문구를 다시 만들거나 감싸지 마라.
    const message = '아이디 또는 비밀번호가 올바르지 않습니다.';
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: FormError(message: message)),
      ),
    );
    expect(find.text(message), findsOneWidget);
  });
}
