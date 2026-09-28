import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../core/push/fake_push.dart';

import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/student/scores/student_score_data.dart';
import 'package:academy_app/features/student/scores/student_scores_page.dart';
import 'package:academy_app/shared/score/score_data.dart';

class _Repo implements StudentScoreRepository {
  _Repo({this.linked = true, this.examsError});

  final bool linked;
  final Object? examsError;

  @override
  Future<StudentMe> me() async => StudentMe(
    name: '김하늘',
    classRooms: const ['A고 2학년 목요일반', '토요 클리닉'],
    phone: '01011112222',
    parentLinked: linked,
  );

  @override
  Future<ScoreData> scores() async =>
      const ScoreData(retestScheduled: [], sections: []);

  @override
  Future<List<ExamSchedule>> exams() async {
    final e = examsError;
    if (e != null) throw e;
    return const [
      ExamSchedule(
        examType: 'MIDTERM',
        startDate: '2026-10-01',
        endDate: '2026-10-05',
        scopeNote: null,
        dDay: 4,
      ),
    ];
  }
}

Future<int Function()> _pump(WidgetTester tester, _Repo repo) async {
  tester.view.physicalSize = const Size(360 * 3, 1800 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final c = StudentScoreController(repository: repo);
  addTearDown(c.dispose);
  final push = fakePushSetting();
  addTearDown(push.dispose);
  var logouts = 0;
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: StudentScoresPage(
          controller: c,
          pushSetting: push,
          onLogout: () async => logouts++,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return () => logouts;
}

void main() {
  testWidgets('내 정보·시험 일정·성적 순이고 제목은 「내 정보 · 성적」이다', (tester) async {
    await _pump(tester, _Repo());
    expect(find.text('내 정보 · 성적'), findsOneWidget);
    expect(find.text('A고 2학년 목요일반 · 토요 클리닉'), findsOneWidget);
    expect(find.text('D-4'), findsOneWidget);
    expect(find.text('아직 기록된 성적이 없습니다.'), findsOneWidget);
    expect(
      tester.getTopLeft(find.byKey(const Key('student-me'))).dy,
      lessThan(tester.getTopLeft(find.byKey(const Key('exam-dday-list'))).dy),
    );
    expect(find.byKey(const Key('parent-not-linked')), findsNothing);
  });

  testWidgets('보호자가 안 붙었으면 선생님께 문의하라고 알린다', (tester) async {
    await _pump(tester, _Repo(linked: false));
    expect(find.byKey(const Key('student-me')), findsOneWidget);
    expect(find.byKey(const Key('parent-not-linked')), findsOneWidget);
  });

  testWidgets('성적을 못 받아도 로그아웃은 있다 — 학생의 유일한 경로다 (14-7)', (tester) async {
    final logouts = await _pump(
      tester,
      _Repo(
        examsError: const ApiException(code: 'INTERNAL', message: '서버 오류입니다.'),
      ),
    );
    expect(find.text('서버 오류입니다.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('student-logout')));
    await tester.pump();
    expect(logouts(), 1);
  });
}
