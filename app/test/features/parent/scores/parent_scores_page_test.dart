import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/scores/parent_score_data.dart';
import 'package:academy_app/features/parent/scores/parent_scores_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/shared/score/score_data.dart';

class _Repo implements ParentScoreRepository {
  final List<int> calls = [];

  @override
  Future<ScoreData> scores(int studentId) async {
    calls.add(studentId);
    return ScoreData(
      retestScheduled: const [],
      sections: [
        ScoreSection(
          testType: 'WORD',
          label: '단어 테스트',
          chartKind: ChartKind.none,
          items: [
            ScoreItem(
              year: 2026,
              month: 9,
              week: 1,
              weekLabel: '9월 1주',
              correctCount: studentId,
              totalCount: 15,
            ),
          ],
        ),
      ],
    );
  }

  @override
  Future<List<ExamSchedule>> exams(int studentId) async => const [
    ExamSchedule(
      examType: 'FINAL',
      startDate: '2026-12-01',
      endDate: '2026-12-03',
      scopeNote: null,
      dDay: 60,
    ),
  ];
}

void main() {
  testWidgets('자녀를 바꾸면 그 아이의 성적을 부르고, 성적이 시험 일정보다 위다', (tester) async {
    tester.view.physicalSize = const Size(360 * 3, 1800 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final dio = Dio(BaseOptions(baseUrl: 'https://x.test'))
      ..httpClientAdapter = FakeAdapter(
        replies: const [
          FakeReply(
            statusCode: 200,
            body: {
              'success': true,
              'data': [
                {'studentId': 1, 'name': '김하늘'},
                {'studentId': 2, 'name': '김바다'},
              ],
            },
          ),
        ],
      );
    final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
    addTearDown(sc.dispose);
    final repo = _Repo();
    final c = ParentScoreController(repository: repo);
    addTearDown(c.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ParentScoresPage(controller: c, selectedChild: sc),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('테스트 결과'), findsOneWidget);
    expect(find.text('1/15'), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('단어 테스트')).dy,
      lessThan(tester.getTopLeft(find.text('시험 일정')).dy),
    );

    await sc.select(2);
    await tester.pumpAndSettle();
    expect(repo.calls, [1, 2]);
    expect(find.text('2/15'), findsOneWidget);
    expect(find.text('1/15'), findsNothing);
  });
}
