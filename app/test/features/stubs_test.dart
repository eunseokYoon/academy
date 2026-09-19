import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/parent/stubs/parent_stubs.dart';
import 'package:academy_app/features/student/stubs/student_stubs.dart';

void main() {
  testWidgets('학생 성적 스텁에 로그아웃이 있다', (tester) async {
    // 웹은 학생 로그아웃을 /student/scores 에 둔다. B3 가 이 스텁을 실물로
    // 바꿀 때까지 여기가 앱의 유일한 학생 로그아웃 경로다.
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(home: StudentScoresStub(onLogout: () async => calls++)),
    );
    expect(find.byKey(const Key('student-logout')), findsOneWidget);
    await tester.tap(find.byKey(const Key('student-logout')));
    await tester.pump();
    expect(calls, 1);
  });

  testWidgets('학생 성적 스텁 제목은 「내 정보 · 성적」이다', (tester) async {
    // 탭 라벨만 「성적」이다. 들어가면 내 정보와 로그아웃이 보여야
    // 공용 PC 나 형제 폰에서 계정을 내려놓을 수 있다.
    await tester.pumpWidget(
      MaterialApp(home: StudentScoresStub(onLogout: () async {})),
    );
    expect(find.text('내 정보 · 성적'), findsOneWidget);
  });

  testWidgets('학부모 내 정보 스텁에 로그아웃이 있다', (tester) async {
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(home: ParentMeStub(onLogout: () async => calls++)),
    );
    expect(find.byKey(const Key('parent-logout')), findsOneWidget);
    await tester.tap(find.byKey(const Key('parent-logout')));
    await tester.pump();
    expect(calls, 1);
  });

  testWidgets('나머지 여섯 스텁은 로그아웃을 갖지 않는다', (tester) async {
    // 로그아웃이 여러 화면에 흩어지면 어디서 내려놓는지 알 수 없다.
    final others = <Widget>[
      const StudentHomeworksStub(),
      const StudentLessonsStub(),
      const StudentQnaStub(),
      const ParentScheduleStub(),
      const ParentLessonsStub(),
      const ParentScoresStub(),
    ];
    for (final w in others) {
      await tester.pumpWidget(MaterialApp(home: w));
      expect(
        find.text('로그아웃'),
        findsNothing,
        reason: '${w.runtimeType} 에 로그아웃이 있다',
      );
    }
  });

  testWidgets('여덟 스텁이 모두 자기 제목을 보여준다', (tester) async {
    final cases = <Widget, String>{
      const StudentHomeworksStub(): '숙제',
      const StudentLessonsStub(): '수업',
      const StudentQnaStub(): '질문',
      const ParentScheduleStub(): '일정',
      const ParentLessonsStub(): '주간 레포트',
      const ParentScoresStub(): '성적',
    };
    for (final e in cases.entries) {
      await tester.pumpWidget(MaterialApp(home: e.key));
      expect(
        find.text(e.value),
        findsOneWidget,
        reason: '${e.key.runtimeType} 의 제목이 다르다',
      );
    }
  });
}
