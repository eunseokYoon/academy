import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/auth/teacher_notice_page.dart';

void main() {
  testWidgets('선생님에게 웹을 안내하고 로그아웃을 준다', (tester) async {
    // 선생님은 앱 대상이 아니다. 로그인은 성공하므로 안내로 받아야 한다 —
    // 빈 화면을 주면 앱이 고장난 것으로 보인다.
    var logouts = 0;
    await tester.pumpWidget(MaterialApp(
      home: TeacherNoticePage(onLogout: () async => logouts++),
    ));

    expect(find.textContaining('웹'), findsOneWidget);

    await tester.tap(find.byKey(const Key('teacher-logout')));
    await tester.pump();
    expect(logouts, 1);
  });
}
