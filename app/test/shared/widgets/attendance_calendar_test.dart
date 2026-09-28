import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/widgets/attendance_calendar.dart';

AttendanceCalendarData _data({
  int present = 0,
  int makeup = 0,
  int? rate,
  List<AttendanceDay> days = const [],
  List<ClinicEntry> clinics = const [],
}) => AttendanceCalendarData(
  month: AttendanceMonth(
    year: 2026,
    month: 9,
    summary: AttendanceSummary(
      present: present,
      late: 0,
      absent: 0,
      sick: 0,
      excused: 0,
      makeup: makeup,
    ),
    homeworkCompletionRate: rate,
    days: days,
  ),
  clinics: clinics,
);

Future<void> _pump(
  WidgetTester tester,
  AttendanceCalendarData data, {
  double width = 390,
  VoidCallback? onPrev,
  VoidCallback? onNext,
}) async {
  tester.view.physicalSize = Size(width * 3, 1600 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: AttendanceCalendar(
            data: data,
            onPrev: onPrev ?? () {},
            onNext: onNext ?? () {},
          ),
        ),
      ),
    ),
  );
}

List<String> _chipTexts(WidgetTester tester, String date) {
  final cell = find.byKey(ValueKey('day-$date'));
  final chips = find.descendant(
    of: cell,
    matching: find.byKey(const Key('session-chip')),
  );
  return [
    for (final chip in chips.evaluate())
      tester
          .widgetList<Text>(
            find.descendant(
              of: find.byWidget(chip.widget),
              matching: find.byType(Text),
            ),
          )
          .map((t) => t.data)
          .join(' '),
  ];
}

void main() {
  testWidgets('2026년 9월 1일은 화요일 칸이다', (tester) async {
    await _pump(tester, _data());
    expect(find.text('2026년 9월'), findsOneWidget);
    final sun = tester.getCenter(find.text('일')).dx;
    final tue = tester.getCenter(find.text('화')).dx;
    final day1 = tester.getCenter(find.byKey(const ValueKey('day-2026-09-01')));
    expect((day1.dx - tue).abs(), lessThan(2));
    expect((day1.dx - sun).abs(), greaterThan(20));
    // 30일까지 있고 31일은 없다.
    expect(find.byKey(const ValueKey('day-2026-09-30')), findsOneWidget);
    expect(find.byKey(const ValueKey('day-2026-09-31')), findsNothing);
  });

  testWidgets('수업이 먼저, 같은 날 클리닉 둘이 그 아래다', (tester) async {
    await _pump(
      tester,
      _data(
        days: const [AttendanceDay(date: '2026-09-08', status: DayStatus.late)],
        clinics: const [
          ClinicEntry(date: '2026-09-08', status: DayStatus.present),
          ClinicEntry(date: '2026-09-08', status: DayStatus.absent),
        ],
      ),
    );
    expect(_chipTexts(tester, '2026-09-08'), ['수업 지각', '클리닉 출석', '클리닉 결석']);
  });

  testWidgets('확정 전 칩은 종류만 적고, 읽어 주는 기계에는 상태까지 들려준다', (tester) async {
    final handle = tester.ensureSemantics();
    await _pump(
      tester,
      _data(
        days: const [
          AttendanceDay(date: '2026-09-15', status: DayStatus.pending),
        ],
      ),
    );
    expect(_chipTexts(tester, '2026-09-15'), ['수업']);
    expect(find.bySemanticsLabel('수업 미확인'), findsOneWidget);
    handle.dispose();
  });

  testWidgets('칩 색이 상태를 따른다 — 결석만 빨강이다', (tester) async {
    await _pump(
      tester,
      _data(
        days: const [
          AttendanceDay(date: '2026-09-01', status: DayStatus.absent),
          AttendanceDay(date: '2026-09-03', status: DayStatus.makeup),
        ],
      ),
    );
    Color bg(String date) {
      final box = tester.widget<Container>(
        find.descendant(
          of: find.byKey(ValueKey('day-$date')),
          matching: find.byKey(const Key('session-chip')),
        ),
      );
      return (box.decoration! as BoxDecoration).color!;
    }

    expect(bg('2026-09-01'), AppColors.red100);
    expect(bg('2026-09-03'), AppColors.teal100);
  });

  testWidgets('요약의 출석 칸은 대체 등원을 포함한다', (tester) async {
    await _pump(tester, _data(present: 4, makeup: 2));
    expect(
      tester.widget<Text>(find.byKey(const ValueKey('summary-출석'))).data,
      '6',
    );
  });

  testWidgets('숙제 완료율은 null 이면 카드째 없고, 있으면 보인다', (tester) async {
    await _pump(tester, _data());
    // 화면이 그려졌음을 먼저 본다(14-12).
    expect(find.text('2026년 9월'), findsOneWidget);
    expect(find.byKey(const Key('homework-rate')), findsNothing);

    await _pump(tester, _data(rate: 75));
    expect(find.text('75%'), findsOneWidget);
  });

  testWidgets('‹ › 가 앞뒤 달을 부른다', (tester) async {
    var prev = 0;
    var next = 0;
    await _pump(tester, _data(), onPrev: () => prev++, onNext: () => next++);
    await tester.tap(find.byKey(const Key('calendar-prev')));
    await tester.tap(find.byKey(const Key('calendar-next')));
    await tester.tap(find.byKey(const Key('calendar-next')));
    expect((prev, next), (1, 2));
  });

  testWidgets('360px 에서 칩이 넘치지 않고 낱말 사이에서만 접힌다', (tester) async {
    // 칸이 40px 남짓이라 「클리닉 대체 등원」은 반드시 접힌다. 낱말 조각이
    // 한 줄짜리(softWrap: false)라야 「대체 등」/「원」처럼 끊기지 않는다.
    await _pump(
      tester,
      _data(
        days: const [
          AttendanceDay(date: '2026-09-02', status: DayStatus.makeup),
        ],
        clinics: const [
          ClinicEntry(date: '2026-09-02', status: DayStatus.excused),
        ],
      ),
      width: 360,
    );
    expect(tester.takeException(), isNull);
    final words = tester.widgetList<Text>(
      find.descendant(
        of: find.byKey(const ValueKey('day-2026-09-02')),
        matching: find.byType(Text),
      ),
    );
    expect(words.map((t) => t.data), containsAll(['대체', '등원', '클리닉', '공결']));
    for (final t in words.skip(1)) {
      expect(t.softWrap, isFalse, reason: '${t.data} 가 낱말 안에서 접힐 수 있다');
    }
  });
}
