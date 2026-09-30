import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/report/parent_report_data.dart';
import 'package:academy_app/features/parent/report/parent_report_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/shared/lesson/lesson_report.dart';
import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/score/score_data.dart';
import 'package:academy_app/shared/widgets/reappear_reload.dart';

const _week = (year: 2026, month: 9, week: 2);

LessonHomework _hw(
  int id, {
  String kind = 'GRID',
  String? result,
  String? status,
}) => LessonHomework(
  homeworkId: id,
  title: '숙제 $id',
  kind: kind,
  dueAt: null,
  result: result,
  completionRate: null,
  resolvedByResubmission: false,
  submissionStatus: status,
);

ParentLessonDetail _lesson(
  int id, {
  DayStatus? attendance,
  LessonHomework? homework,
  List<LessonHomework>? homeworks,
  String? keyPoints,
}) => ParentLessonDetail(
  lessonId: id,
  lessonDate: '2026-09-0$id',
  title: '수업 $id',
  notes: LessonNotes(
    content: '내용 $id',
    keyPoints: keyPoints,
    homeworkNote: null,
    clinicNote: null,
  ),
  homeworks: homeworks ?? [?homework],
  attendance: attendance,
);

const _scores = ScoreData(
  retestScheduled: [],
  sections: [
    ScoreSection(
      testType: 'PRACTICE',
      label: '실전 모의고사',
      chartKind: ChartKind.bar,
      items: [
        ScoreItem(
          year: 2026,
          month: 9,
          week: 1,
          weekLabel: '9월 1주',
          correctCount: 10,
          totalCount: 20,
        ),
        ScoreItem(
          year: 2026,
          month: 9,
          week: 2,
          weekLabel: '9월 2주',
          correctCount: 14,
          totalCount: 20,
        ),
        ScoreItem(
          year: 2026,
          month: 9,
          week: 3,
          weekLabel: '9월 3주',
          correctCount: 18,
          totalCount: 20,
        ),
      ],
    ),
    // 그 주에 기록이 없는 종류 — 줄을 만들면 안 본 시험이 0점처럼 읽힌다.
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
          correctCount: 12,
          totalCount: 15,
        ),
      ],
    ),
  ],
);

class _Repo implements ParentReportRepository {
  _Repo({this.lessons = const [], this.detailError});

  final List<ParentLessonDetail> lessons;
  final Object? detailError;
  final List<(int, ReportWeek)> weekCalls = [];

  @override
  Future<(String, List<String>)> header(int studentId) async =>
      ('김하늘', ['A반', '토요 클리닉']);

  @override
  Future<List<int>> lessonIds(int studentId, ReportWeek w) async {
    weekCalls.add((studentId, w));
    return [for (final l in lessons) l.lessonId];
  }

  @override
  Future<ParentLessonDetail> lesson(int studentId, int lessonId) async {
    final e = detailError;
    if (e != null) throw e;
    return lessons.firstWhere((l) => l.lessonId == lessonId);
  }

  @override
  Future<List<DayStatus?>> clinics(int studentId, ReportWeek w) async => const [
    DayStatus.present,
    null,
  ];

  @override
  Future<ScoreData> scores(int studentId) async => _scores;
}

Future<void> _pump(WidgetTester tester, _Repo repo) async {
  tester.view.physicalSize = const Size(360 * 3, 2400 * 3);
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
            ],
          },
        ),
      ],
    );
  final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
  addTearDown(sc.dispose);
  final c = ParentReportController(repository: repo);
  addTearDown(c.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: ParentReportPage(
          controller: c,
          selectedChild: sc,
          now: DateTime(2026, 9, 10),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

String _stat(WidgetTester tester, String label) =>
    tester.widget<Text>(find.byKey(ValueKey('report-stat-$label'))).data!;

void main() {
  test('처음 고르는 주는 1일부터 7일씩 끊은 주차다', () {
    expect(currentReportWeek(DateTime(2026, 9, 7)).week, 1);
    expect(currentReportWeek(DateTime(2026, 9, 8)).week, 2);
    expect(currentReportWeek(DateTime(2026, 9, 29)).week, 5);
  });

  group('엮기', () {
    test('그 주 기록이 있는 종류만, 흐름은 그 주까지 자른다', () {
      final r = buildWeeklyReport(
        week: _week,
        name: '김하늘',
        classRooms: const [],
        lessons: const [],
        clinics: const [],
        scores: _scores,
      );
      expect(r.tests.map((t) => t.section.testType), ['PRACTICE']);
      expect(r.tests.single.item.correctCount, 14);
      expect(r.tests.single.history.map((i) => i.week), [1, 2]);
    });

    test('출석은 수업과 클리닉을 함께 세고, 지각·대체 등원은 오고 미확인은 안 센다', () {
      final r = buildWeeklyReport(
        week: _week,
        name: '',
        classRooms: const [],
        lessons: [
          _lesson(1, attendance: DayStatus.late),
          _lesson(2, attendance: DayStatus.makeup),
          _lesson(3, attendance: DayStatus.absent),
          _lesson(4),
        ],
        clinics: const [DayStatus.present, null, DayStatus.absent],
        scores: const ScoreData(retestScheduled: [], sections: []),
      );
      expect(r.attended, 3);
      expect(r.clinicCount, 3);
    });

    test('숙제 완료는 GRID 면 ⭕, ONLINE 이면 제출이다 (4-2)', () {
      final r = buildWeeklyReport(
        week: _week,
        name: '',
        classRooms: const [],
        lessons: [
          _lesson(
            1,
            homework: _hw(1, result: 'DONE', status: 'NOT_SUBMITTED'),
          ),
          _lesson(2, homework: _hw(2, result: 'PARTIAL')),
          _lesson(
            3,
            homework: _hw(3, kind: 'ONLINE', status: 'SUBMITTED'),
          ),
          _lesson(4),
        ],
        clinics: const [],
        scores: const ScoreData(retestScheduled: [], sections: []),
      );
      expect(r.homeworks.length, 3);
      expect(r.doneHomeworks, 2);
    });

    test('한 수업에 숙제가 여럿이면 전부 든다(2026-09-29)', () {
      // 첫 숙제만 붙이던 때 레포트에 하나만 떠서 숙제 탭과 개수가 달랐다.
      final r = buildWeeklyReport(
        week: _week,
        name: '',
        classRooms: const [],
        lessons: [
          _lesson(
            1,
            homeworks: [
              _hw(1, result: 'DONE'),
              _hw(2, result: 'NOT_DONE'),
            ],
          ),
        ],
        clinics: const [],
        scores: const ScoreData(retestScheduled: [], sections: []),
      );
      expect(r.homeworks.map((h) => h.$2.homeworkId), [1, 2]);
      expect(r.doneHomeworks, 1);
    });
  });

  group('수업 상세 JSON', () {
    Map<String, dynamic> hwJson(int id) => {
      'homeworkId': id,
      'title': '숙제 $id',
      'description': null,
      'kind': 'GRID',
      'dueAt': null,
      'result': null,
      'completionRate': null,
      'resolvedByResubmission': false,
      'submissionStatus': null,
    };
    Map<String, dynamic> lessonJson(Map<String, dynamic> extra) => {
      'lessonId': 1,
      'lessonDate': '2026-09-21',
      'title': null,
      'content': null,
      'keyPoints': null,
      'homeworkNote': null,
      'clinicNote': null,
      'attendanceStatus': null,
      ...extra,
    };

    test('homeworks 가 있으면 전부 읽는다', () {
      final d = ParentLessonDetail.fromJson(
        lessonJson({
          'homework': hwJson(1),
          'homeworks': [hwJson(1), hwJson(2)],
        }),
      );
      expect(d.homeworks.map((h) => h.homeworkId), [1, 2]);
    });

    test('영상 시청 현황을 읽고, 없거나 모르면 null 이다(줄을 안 그린다)', () {
      expect(
        ParentLessonDetail.fromJson(lessonJson({'videoWatch': 'PARTIAL'}))
            .videoWatch,
        VideoWatchState.partial,
      );
      expect(ParentLessonDetail.fromJson(lessonJson({})).videoWatch, isNull);
      expect(
        ParentLessonDetail.fromJson(lessonJson({'videoWatch': 'X'})).videoWatch,
        isNull,
      );
    });

    test('온라인은 온 것으로 센다(2026-09-29)', () {
      final r = buildWeeklyReport(
        week: _week,
        name: '',
        classRooms: const [],
        lessons: [
          _lesson(1, attendance: DayStatus.online),
          _lesson(2),
        ],
        clinics: const [],
        scores: const ScoreData(retestScheduled: [], sections: []),
      );
      expect(r.attended, 1);
    });

    test('옛 서버(homeworks 없음)는 첫 숙제 하나로 받는다(7-3)', () {
      final d = ParentLessonDetail.fromJson(
        lessonJson({'homework': hwJson(1)}),
      );
      expect(d.homeworks.map((h) => h.homeworkId), [1]);
      final none = ParentLessonDetail.fromJson(lessonJson({'homework': null}));
      expect(none.homeworks, isEmpty);
    });
  });

  group('처음 뜨는 주(2026-09-29)', () {
    // 셸이 State 를 살려 둬서 한 번 정한 주가 몇 주씩 남았다 — 9월에 8월 5주가 떴다.
    late ParentReportController c;
    late SelectedChild sc;
    late _Repo repo;

    Future<void> pumpAt(WidgetTester tester, DateTime now) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ParentReportPage(controller: c, selectedChild: sc, now: now),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> setUpPage(WidgetTester tester) async {
      tester.view.physicalSize = const Size(360 * 3, 2400 * 3);
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
                ],
              },
            ),
          ],
        );
      sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
      addTearDown(sc.dispose);
      repo = _Repo();
      c = ParentReportController(repository: repo);
      addTearDown(c.dispose);
    }

    String label(WidgetTester tester) => tester
        .widget<Text>(find.byKey(const Key('report-week-label')))
        .textSpan!
        .toPlainText();

    testWidgets('주가 바뀐 뒤 다시 보이면 이번 주로 돌아온다', (tester) async {
      await setUpPage(tester);
      await pumpAt(tester, DateTime(2026, 8, 31));
      expect(label(tester), '8월 5주');

      // 며칠 뒤 같은 State 가 다시 보인다(탭 재진입·앱 복귀가 같은 경로다).
      await pumpAt(tester, DateTime(2026, 9, 29));
      PushArrivals.instance.arrived();
      await tester.pumpAndSettle();

      expect(label(tester), '9월 5주');
      expect(repo.weekCalls.last, (1, (year: 2026, month: 9, week: 5)));
    });

    testWidgets('같은 주 안에서 고른 지난 주차는 다시 보여도 남긴다', (tester) async {
      await setUpPage(tester);
      await pumpAt(tester, DateTime(2026, 9, 29));
      await tester.tap(find.byKey(const Key('report-week')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('2주차').last);
      await tester.pumpAndSettle();
      expect(label(tester), '9월 2주');

      PushArrivals.instance.arrived();
      await tester.pumpAndSettle();
      expect(label(tester), '9월 2주');

      // 주가 넘어가면 그때는 이번 주다.
      await pumpAt(tester, DateTime(2026, 10, 1));
      PushArrivals.instance.arrived();
      await tester.pumpAndSettle();
      expect(label(tester), '10월 1주');
    });
  });

  group('화면', () {
    testWidgets('표지·수업·테스트·숙제가 한 장에 그려진다', (tester) async {
      await _pump(
        tester,
        _Repo(
          lessons: [
            _lesson(
              1,
              attendance: DayStatus.present,
              homework: _hw(1, result: 'DONE', status: 'NOT_SUBMITTED'),
              keyPoints: '관계대명사 복습',
            ),
            _lesson(2),
          ],
        ),
      );
      expect(find.text('김하늘'), findsWidgets);
      expect(find.text('A반 · 토요 클리닉'), findsOneWidget);
      expect(_stat(tester, '수업'), '2회');
      expect(_stat(tester, '클리닉'), '2회');
      expect(_stat(tester, '출석'), '2회');
      expect(_stat(tester, '테스트'), '1건');
      expect(_stat(tester, '숙제'), '1/1');
      // 확정 전 수업은 「출석 미확인」이다 — 결석이 아니다.
      expect(find.text('출석 미확인'), findsOneWidget);
      expect(find.byKey(const Key('report-keypoints')), findsOneWidget);
      expect(find.text('14/20'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('report-chart-PRACTICE')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('report-test-WORD')), findsNothing);
      expect(find.text('완료'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('빈 주는 왜 비었는지 적는다', (tester) async {
      await _pump(tester, _Repo());
      expect(find.byKey(const Key('report-letterhead')), findsOneWidget);
      expect(find.text('이 주에 공개된 수업이 없습니다.'), findsOneWidget);
      expect(find.text('이 주에 나간 숙제가 없습니다.'), findsOneWidget);
    });

    testWidgets('주차를 바꾸면 그 주를 부르고 표지의 주가 바뀐다', (tester) async {
      final repo = _Repo();
      await _pump(tester, repo);
      expect(repo.weekCalls.single, (1, _week));
      await tester.tap(find.byKey(const Key('report-week')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('4주차').last);
      await tester.pumpAndSettle();
      expect(repo.weekCalls.last, (1, (year: 2026, month: 9, week: 4)));
      expect(
        tester
            .widget<Text>(find.byKey(const Key('report-week-label')))
            .textSpan!
            .toPlainText(),
        '9월 4주',
      );
    });

    testWidgets('수업 상세 하나가 실패해도 서버 문구를 그대로 보여준다', (tester) async {
      await _pump(
        tester,
        _Repo(
          lessons: [_lesson(1)],
          detailError: const ApiException(
            code: 'FORBIDDEN',
            message: '접근 권한이 없습니다.',
          ),
        ),
      );
      expect(find.text('접근 권한이 없습니다.'), findsOneWidget);
      // 표지와 주 선택은 남는다 — 다른 주로 옮길 수 있어야 한다.
      expect(find.byKey(const Key('report-week')), findsOneWidget);
    });
  });
}
