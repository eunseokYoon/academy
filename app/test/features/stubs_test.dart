import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/student/stubs/student_stubs.dart';

/// B3 까지 끝나고 남은 스텁은 B4 몫 셋이다. 로그아웃은 실물 화면(학생 성적 ·
/// 학부모 내 정보)이 들고 있다 — 스텁에 흩어지면 어디서 내려놓는지 알 수 없다.
void main() {
  testWidgets('남은 스텁은 자기 제목을 보여주고 로그아웃을 갖지 않는다', (tester) async {
    final cases = <Widget, String>{
      const StudentQnaStub(): '질문',
      const StudentClinicsStub(): '스케줄',
      const StudentOnlineTestsStub(): '테스트',
    };
    for (final e in cases.entries) {
      await tester.pumpWidget(MaterialApp(home: e.key));
      expect(find.text(e.value), findsOneWidget);
      expect(find.text('로그아웃'), findsNothing);
    }
  });
}
