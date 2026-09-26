import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/chart_colors.dart';
import 'package:academy_app/shared/score/correct_count_chart.dart';
import 'package:academy_app/shared/score/exam_dday_list.dart';
import 'package:academy_app/shared/score/score_data.dart';
import 'package:academy_app/shared/score/score_section_list.dart';
import 'package:academy_app/shared/score/score_value.dart';

ScoreItem _i(
  int week, {
  int? correct,
  int? total,
  int? ic,
  int? it,
  int? ec,
  int? et,
  String? result,
  bool retestScheduled = false,
  bool retestPassed = false,
}) => ScoreItem(
  year: 2026,
  month: 9,
  week: week,
  weekLabel: '9월 $week주',
  correctCount: correct,
  totalCount: total,
  internalCorrect: ic,
  internalTotal: it,
  externalCorrect: ec,
  externalTotal: et,
  result: result,
  retestScheduled: retestScheduled,
  retestPassed: retestPassed,
);

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(360 * 3, 2000 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
}

void main() {
  group('응답', () {
    test('chartKind 를 읽고, 모르는 값은 그래프 없음이다', () {
      expect(ChartKind.parse('BAR'), ChartKind.bar);
      expect(ChartKind.parse('SPLIT_BAR'), ChartKind.splitBar);
      expect(ChartKind.parse('NONE'), ChartKind.none);
      expect(ChartKind.parse('LINE'), ChartKind.none);
    });

    test('옛 응답에 배열이 없어도 빈 목록으로 받는다 (7-3)', () {
      final d = ScoreData.fromJson(const {});
      expect(d.sections, isEmpty);
      expect(d.retestScheduled, isEmpty);
    });

    test('주차 키는 연·월·주 순으로 커진다', () {
      expect(weekKey(2026, 8, 5) < weekKey(2026, 9, 1), isTrue);
      expect(weekKey(2025, 12, 5) < weekKey(2026, 1, 1), isTrue);
    });
  });

  group('막대 계산', () {
    test('Y축은 구획 전체의 가장 큰 전체 문항 수 하나로 고정한다', () {
      final l = layoutChart([
        _i(1, correct: 10, total: 20),
        _i(2, correct: 25, total: 30),
      ], ChartKind.bar)!;
      expect(l.yMax, 30);
      expect(l.ticks.map((t) => t.$1), [0, 15, 30]);
      // 같은 축이라 20문항 중 10개가 30문항 중 25개보다 낮다.
      expect(l.bars[0].rect.height, lessThan(l.bars[1].rect.height));
    });

    test('전체 문항 수가 없는 주는 막대를 안 그린다 — 0 으로 채우지 않는다', () {
      final l = layoutChart([
        _i(1, correct: 10, total: 20),
        _i(2, correct: 5),
      ], ChartKind.bar)!;
      expect(l.bars, hasLength(1));
      expect(l.labels.map((x) => x.text), ['9월 1주']);
    });

    test('그릴 것이 없거나 NONE 이면 null 이다', () {
      expect(layoutChart([_i(1, result: 'PASS')], ChartKind.bar), isNull);
      expect(
        layoutChart([_i(1, correct: 1, total: 2)], ChartKind.none),
        isNull,
      );
    });

    test('클리닉은 내부(버건디)·외부(남색) 두 막대가 한 칸에 붙지 않고 들어간다', () {
      final items = [
        for (var w = 1; w <= 12; w++) _i(w, ic: 5, it: 10, ec: 7, et: 10),
      ];
      final l = layoutChart(items, ChartKind.splitBar)!;
      expect(l.bars, hasLength(24));
      expect(l.bars[0].color, ChartColors.internal);
      expect(l.bars[1].color, ChartColors.external);
      // 한 주의 오른쪽 막대 끝이 다음 주의 왼쪽 막대 시작을 넘지 않는다. 웹 식은
      // 두 막대 + 사이 2px 가 칸에 정확히 들어가 경계에서 딱 맞닿는다(부동소수 오차만 허용).
      for (var w = 0; w < 11; w++) {
        expect(
          l.bars[w * 2 + 1].rect.right,
          lessThanOrEqualTo(l.bars[w * 2 + 2].rect.left + 1e-9),
        );
      }
    });

    test('표시한 주는 주황이고, 라벨을 줄여도 그 주는 적는다', () {
      final items = [for (var w = 1; w <= 9; w++) _i(w, correct: w, total: 10)];
      final l = layoutChart(
        items,
        ChartKind.bar,
        highlight: weekKey(2026, 9, 3),
      )!;
      expect(l.bars[2].color, ChartColors.marked);
      expect(l.bars[0].color, ChartColors.external);
      // 9주라 양 끝·가운데만 적는데 표시한 3주는 따로 남는다.
      expect(l.labels.map((x) => x.text), ['9월 1주', '9월 3주', '9월 5주', '9월 9주']);
      expect(l.labels[1].marked, isTrue);
    });

    testWidgets('두 계열일 때만 범례가 있다', (tester) async {
      await _pump(
        tester,
        CorrectCountChart(
          items: [_i(1, ic: 1, it: 2, ec: 1, et: 2)],
          chartKind: ChartKind.splitBar,
        ),
      );
      expect(find.text('내부지문'), findsOneWidget);
      expect(find.text('내부 · 외부 맞힌 개수 · 전체 2문항'), findsOneWidget);

      await _pump(
        tester,
        CorrectCountChart(
          items: [_i(1, correct: 1, total: 2)],
          chartKind: ChartKind.bar,
        ),
      );
      expect(find.text('맞힌 개수 · 전체 2문항'), findsOneWidget);
      expect(find.text('내부지문'), findsNothing);
    });
  });

  group('값·배지', () {
    test('클리닉은 내부·외부가 먼저고, 리뷰는 글자가 없다', () {
      expect(
        scoreValueText(_i(1, ic: 3, it: 5, ec: null, et: 6)),
        '내부 3/5 · 외부 —/6',
      );
      expect(scoreValueText(_i(1, correct: 12, total: 15)), '12/15');
      expect(scoreValueText(_i(1, result: 'PASS')), isNull);
    });

    testWidgets('예정이 통과보다 먼저다 — 둘 다 FAIL 이라서', (tester) async {
      Future<String?> label(ScoreItem item) async {
        await _pump(tester, ScoreResultBadge(item: item));
        final t = find.descendant(
          of: find.byKey(const Key('score-result')),
          matching: find.byType(Text),
        );
        return t.evaluate().isEmpty ? null : tester.widget<Text>(t).data;
      }

      expect(
        await label(_i(1, result: 'FAIL', retestScheduled: true)),
        '재시험 예정',
      );
      expect(await label(_i(1, result: 'FAIL', retestPassed: true)), '재시험 통과');
      expect(await label(_i(1, result: 'PASS')), '통과');
      expect(await label(_i(1, correct: 3, total: 5)), isNull);
    });
  });

  group('성적 목록', () {
    testWidgets('최신 주가 위이고, 여섯 줄부터 구획 안에서 스크롤한다', (tester) async {
      final items = [for (var w = 1; w <= 6; w++) _i(w, correct: w, total: 10)];
      await _pump(
        tester,
        ScoreSectionList(
          data: ScoreData(
            retestScheduled: const [],
            sections: [
              ScoreSection(
                testType: 'WORD',
                label: '단어 테스트',
                chartKind: ChartKind.none,
                items: items,
              ),
            ],
          ),
        ),
      );
      final y6 = tester
          .getTopLeft(find.byKey(ValueKey('score-row-WORD-${items[5].key}')))
          .dy;
      final y5 = tester
          .getTopLeft(find.byKey(ValueKey('score-row-WORD-${items[4].key}')))
          .dy;
      expect(y6, lessThan(y5));
      expect(find.byKey(const Key('score-rows-scroll')), findsOneWidget);
      // 단어는 NONE — 그래프가 없다.
      expect(find.byKey(const Key('score-chart')), findsNothing);
    });

    testWidgets('재시험 예정이 맨 위에 서버 문장 그대로 있고, 성적이 없으면 안내다', (tester) async {
      await _pump(
        tester,
        const ScoreSectionList(
          data: ScoreData(
            retestScheduled: [
              RetestNotice(
                testType: 'WORD',
                weekLabel: '9월 2주',
                label: '9월 2주 단어 테스트 재시험',
              ),
            ],
            sections: [],
          ),
        ),
      );
      expect(find.text('9월 2주 단어 테스트 재시험'), findsOneWidget);
      expect(find.text('아직 기록된 성적이 없습니다.'), findsOneWidget);
    });
  });

  group('시험 일정', () {
    ExamSchedule e(String type, int dDay) => ExamSchedule(
      examType: type,
      startDate: '2026-10-01',
      endDate: '2026-10-05',
      scopeNote: null,
      dDay: dDay,
    );

    testWidgets('없으면 통째로 없다', (tester) async {
      await _pump(tester, const ExamDdayList(schedules: []));
      expect(find.byKey(const Key('exam-dday-list')), findsNothing);
    });

    testWidgets('다가오는 것이 먼저, 지난 것은 아래에 「N일 전」이다', (tester) async {
      await _pump(
        tester,
        ExamDdayList(schedules: [e('MIDTERM', -3), e('FINAL', 0)]),
      );
      expect(find.text('오늘'), findsOneWidget);
      expect(find.text('3일 전'), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('기말고사')).dy,
        lessThan(tester.getTopLeft(find.text('중간고사')).dy),
      );
      expect(find.text('2026.10.01 ~ 10.05'), findsNWidgets(2));
    });
  });
}
