import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/schedule/parent_schedule_data.dart';
import 'package:academy_app/features/parent/schedule/parent_schedule_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';
import 'package:academy_app/shared/lib/attendance.dart';

class _Repo implements ParentScheduleRepository {
  final List<(int, YearMonth)> calls = [];

  @override
  Future<AttendanceMonth> month(int studentId, YearMonth ym) async {
    calls.add((studentId, ym));
    return AttendanceMonth(
      year: ym.year,
      month: ym.month,
      summary: AttendanceSummary(
        present: studentId * 10,
        late: 0,
        absent: 0,
        sick: 0,
        excused: 0,
        makeup: 0,
      ),
      homeworkCompletionRate: null,
      days: const [],
    );
  }

  @override
  Future<List<ClinicEntry>> clinics(int studentId, YearMonth ym) async =>
      const [];
}

const _error = FakeReply(
  statusCode: 500,
  body: {
    'success': false,
    'data': null,
    'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
  },
);

FakeReply _kids(List<(int, String)> kids) => FakeReply(
  statusCode: 200,
  body: {
    'success': true,
    'data': [
      for (final (id, name) in kids) {'studentId': id, 'name': name},
    ],
  },
);

Future<(_Repo, SelectedChild)> _pump(
  WidgetTester tester, {
  required List<FakeReply> childReplies,
}) async {
  tester.view.physicalSize = const Size(390 * 3, 1600 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
    ..httpClientAdapter = FakeAdapter(replies: childReplies);
  final sc = SelectedChild(dio: dio, store: InMemoryKeyValueStore());
  final repo = _Repo();
  final c = ParentScheduleController(repository: repo);
  addTearDown(c.dispose);
  addTearDown(sc.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: ParentSchedulePage(controller: c, selectedChild: sc),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (repo, sc);
}

String _attended(WidgetTester tester) =>
    tester.widget<Text>(find.byKey(const ValueKey('summary-출석'))).data!;

void main() {
  testWidgets('홈을 거치지 않고 열려도 자녀 목록을 받아 첫째의 이번 달을 부른다', (tester) async {
    final (repo, _) = await _pump(
      tester,
      childReplies: [
        _kids([(1, '김하늘'), (2, '김바다')]),
      ],
    );
    final now = DateTime.now();
    expect(repo.calls, [(1, (year: now.year, month: now.month))]);
    expect(find.text('수업 · 클리닉 일정'), findsOneWidget);
    expect(_attended(tester), '10');
  });

  testWidgets('자녀를 바꾸면 보던 달 그대로 그 아이를 부른다', (tester) async {
    final (repo, sc) = await _pump(
      tester,
      childReplies: [
        _kids([(1, '김하늘'), (2, '김바다')]),
      ],
    );
    await tester.tap(find.byKey(const Key('calendar-prev')));
    await tester.pumpAndSettle();
    final seen = repo.calls.last.$2;

    await sc.select(2);
    await tester.pumpAndSettle();
    expect(repo.calls.last, (2, seen));
    expect(_attended(tester), '20');
  });

  testWidgets('자녀 목록을 못 받으면 로더가 아니라 오류와 다시 시도다', (tester) async {
    // 홈이 띄우던 오류를 이 화면도 띄워야 한다 — 탭은 홈을 거치지 않고 열린다.
    final (repo, _) = await _pump(
      tester,
      childReplies: [
        _error,
        _kids([(1, '김하늘')]),
      ],
    );
    expect(find.text('서버 오류입니다.'), findsOneWidget);
    expect(repo.calls, isEmpty);

    await tester.tap(find.text('다시 시도'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('children-error')), findsNothing);
    expect(repo.calls.single.$1, 1);
    expect(_attended(tester), '10');
  });

  testWidgets('자녀가 0명이면 안내를 띄운다', (tester) async {
    final (repo, _) = await _pump(tester, childReplies: [_kids([])]);
    expect(find.text('연결된 자녀가 없습니다.'), findsOneWidget);
    expect(repo.calls, isEmpty);
  });

  testWidgets('자녀가 하나면 제목 줄에 선택이 없다', (tester) async {
    await _pump(
      tester,
      childReplies: [
        _kids([(1, '김하늘')]),
      ],
    );
    expect(find.text('수업 · 클리닉 일정'), findsOneWidget);
    expect(find.byKey(const Key('title-child-select')), findsNothing);
  });
}
