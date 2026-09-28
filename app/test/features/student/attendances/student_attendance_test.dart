import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/features/student/attendances/student_attendance_data.dart';
import 'package:academy_app/features/student/attendances/student_attendance_page.dart';
import 'package:academy_app/shared/lib/attendance.dart';

AttendanceMonth _month(YearMonth ym) => AttendanceMonth(
  year: ym.year,
  month: ym.month,
  summary: const AttendanceSummary(
    present: 1,
    late: 0,
    absent: 0,
    sick: 0,
    excused: 0,
    makeup: 0,
  ),
  homeworkCompletionRate: null,
  days: const [],
);

class _Repo implements StudentAttendanceRepository {
  final List<YearMonth> monthCalls = [];
  Object? clinicsError;

  @override
  Future<AttendanceMonth> month(YearMonth ym) async {
    monthCalls.add(ym);
    return _month(ym);
  }

  @override
  Future<List<ClinicEntry>> clinics(YearMonth ym) async {
    final e = clinicsError;
    if (e != null) throw e;
    return const [];
  }
}

Future<StudentAttendanceController> _pump(
  WidgetTester tester,
  _Repo repo,
) async {
  final c = StudentAttendanceController(repository: repo);
  addTearDown(c.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: StudentAttendancePage(controller: c)),
    ),
  );
  await tester.pumpAndSettle();
  return c;
}

void main() {
  test('클리닉은 내 예약이 있는 것만 칩이 된다', () async {
    // 이 응답은 열린 클리닉을 전부 준다(이동 후보). 거르지 않으면 남의 시간대가
    // 내 출결로 보인다.
    final adapter = FakeAdapter(
      replies: [
        const FakeReply(
          statusCode: 200,
          body: {
            'success': true,
            'data': [
              {'clinicDate': '2026-09-02', 'myReservation': null},
              {
                'clinicDate': '2026-09-04',
                'myReservation': {'attendStatus': null},
              },
              {
                'clinicDate': '2026-09-09',
                'myReservation': {'attendStatus': 'LATE'},
              },
            ],
          },
        ),
      ],
    );
    final dio = Dio(BaseOptions(baseUrl: 'https://example.test'))
      ..httpClientAdapter = adapter;
    final entries = await StudentAttendanceRepository(dio)
        .clinics((year: 2026, month: 9));

    expect(adapter.received.single.path, '/api/student/clinics');
    expect(adapter.received.single.queryParameters, {
      'from': '2026-09-01',
      'to': '2026-09-30',
    });
    expect(entries.map((e) => (e.date, e.status)), [
      ('2026-09-04', DayStatus.pending),
      ('2026-09-09', DayStatus.late),
    ]);
  });

  testWidgets('이번 달을 부르고 › 로 다음 달을 부른다', (tester) async {
    final repo = _Repo();
    await _pump(tester, repo);
    final now = DateTime.now();
    final thisMonth = (year: now.year, month: now.month);
    expect(repo.monthCalls, [thisMonth]);
    expect(find.text('${now.year}년 ${now.month}월'), findsOneWidget);

    await tester.tap(find.byKey(const Key('calendar-next')));
    await tester.pumpAndSettle();
    final next = shiftMonth(thisMonth, 1);
    expect(repo.monthCalls.last, next);
    expect(find.text('${next.year}년 ${next.month}월'), findsOneWidget);
  });

  testWidgets('둘 중 하나가 실패하면 서버 문구를 그대로 보여준다', (tester) async {
    // 레코드 `.wait` 로 기다리면 ParallelWaitError 로 감싸여 「연결할 수
    // 없습니다」로 바뀐다(param_controller.dart 의 both 주석).
    final repo = _Repo()
      ..clinicsError = const ApiException(
        code: 'FORBIDDEN',
        message: '접근 권한이 없습니다.',
      );
    await _pump(tester, repo);
    expect(find.text('접근 권한이 없습니다.'), findsOneWidget);
    expect(find.text('다시 시도'), findsOneWidget);
  });

  testWidgets('다시 들어오면 보던 달이다', (tester) async {
    final repo = _Repo();
    final c = await _pump(tester, repo);
    await tester.tap(find.byKey(const Key('calendar-prev')));
    await tester.pumpAndSettle();
    final seen = c.param;

    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: StudentAttendancePage(controller: c)),
      ),
    );
    await tester.pumpAndSettle();
    expect(c.param, seen);
    expect(find.text('${seen!.year}년 ${seen.month}월'), findsOneWidget);
  });
}
