import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/attendance.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/lesson/lesson_report.dart';
import '../../../shared/score/score_data.dart';
import '../scores/parent_score_data.dart';

/// 레포트가 보는 주. [week] 는 1~5(달 안에서 1일부터 7일씩, 서버 `MonthWeeks`).
typedef ReportWeek = ({int year, int month, int week});

/// 선택 상자의 **첫 값만** 오늘에서 뽑는다(웹 `currentWeek` 과 같은 규칙).
///
/// 이것으로 데이터를 주차에 묶지 마라(9-2) — 묶는 일은 서버가 하고, 화면은 고른
/// 숫자를 그대로 넘긴다. 표시용 오늘이라 기기 시계를 쓴다.
ReportWeek currentReportWeek([DateTime? now]) {
  now ??= DateTime.now();
  return (year: now.year, month: now.month, week: (now.day - 1) ~/ 7 + 1);
}

/// 그 주의 한 종류 — 그 주 값과, 그 주까지의 흐름.
class WeekTest {
  const WeekTest({
    required this.section,
    required this.item,
    required this.history,
  });

  final ScoreSection section;
  final ScoreItem item;

  /// 고른 주까지 자른 시계열. 그 뒤 주차는 아직 안 일어난 일이라 그리지 않는다.
  final List<ScoreItem> history;
}

/// P-6 한 장.
class WeeklyReport {
  const WeeklyReport({
    required this.name,
    required this.classRooms,
    required this.lessons,
    required this.clinicCount,
    required this.attended,
    required this.tests,
  });

  final String name;
  final List<String> classRooms;
  final List<ParentLessonDetail> lessons;
  final int clinicCount;

  /// 그 주에 학원에 간 횟수 — **수업과 클리닉을 함께 센다**(둘 다 갔으면 2회).
  /// 학부모가 세는 것은 출석부가 아니라 아이가 간 횟수다.
  final int attended;
  final List<WeekTest> tests;

  /// 그 주 수업에 딸린 숙제 **전부**. 어느 수업의 것인지 날짜와 함께 든다.
  List<(String, LessonHomework)> get homeworks => [
    for (final l in lessons)
      for (final h in l.homeworks) (l.lessonDate, h),
  ];

  int get doneHomeworks => homeworks.where((h) => h.$2.done).length;
}

/// 학원에 **온** 날로 세는 상태. 지각도 온 것이다. **대체 등원도 온 것이다**(5-1,
/// 2026-09-27 확정). 웹 `ParentReportPage` 의 `came` 과 같은 규칙이다 — 한쪽만
/// 고치면 같은 주가 웹과 앱에서 다른 숫자가 된다. 미확인은 아직 모르는 것이라 세지 않는다.
bool _came(DayStatus? s) =>
    s == DayStatus.present ||
    s == DayStatus.late ||
    s == DayStatus.makeup ||
    s == DayStatus.online;

/// 받은 것들을 한 장으로 엮는다. 계산이 전부 여기 있다 — 테스트가 직접 부른다.
///
/// **기록이 없는 종류는 줄을 만들지 않는다.** 4종을 빈칸으로 늘어놓으면 안 본
/// 시험이 0점처럼 읽힌다(7-1 「기입 안 하면 안 보이게」).
WeeklyReport buildWeeklyReport({
  required ReportWeek week,
  required String name,
  required List<String> classRooms,
  required List<ParentLessonDetail> lessons,
  required List<DayStatus?> clinics,
  required ScoreData scores,
}) {
  final selected = weekKey(week.year, week.month, week.week);
  final tests = <WeekTest>[
    for (final s in scores.sections)
      if (s.items.where((i) => i.key == selected).firstOrNull case final item?)
        WeekTest(
          section: s,
          item: item,
          history: s.items.where((i) => i.key <= selected).toList(),
        ),
  ];
  return WeeklyReport(
    name: name,
    classRooms: classRooms,
    lessons: lessons,
    clinicCount: clinics.length,
    attended:
        lessons.where((l) => _came(l.attendance)).length +
        clinics.where(_came).length,
    tests: tests,
  );
}

/// **레포트 전용 API 를 만들지 않았다**(웹과 같다). 기존 넷을 조립한다 — 홈(이름·반),
/// 주차별 수업 목록 + 수업마다 상세, 그 주 클리닉, 성적. 주에 수업이 한두 번이라
/// 상세 호출도 한두 번이다. 조립이 무거워지면 그때 재 봐라.
class ParentReportRepository {
  const ParentReportRepository(this._dio, this._scores);

  final Dio _dio;
  final ParentScoreRepository _scores;

  /// 이름·반. 학부모 홈 응답에서 두 칸만 읽는다.
  Future<(String, List<String>)> header(int studentId) => unwrapCall(
    () =>
        _dio.get<Map<String, dynamic>>('/api/parent/children/$studentId/home'),
    (data) {
      final student =
          (data! as Map<String, dynamic>)['student'] as Map<String, dynamic>;
      return (
        student['name'] as String,
        ((student['classRooms'] as List<dynamic>?) ?? const [])
            .cast<String>()
            .toList(),
      );
    },
  );

  /// 그 주에 공개된 수업의 id. **주차 → 날짜 변환은 서버가 한다.**
  Future<List<int>> lessonIds(int studentId, ReportWeek w) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/lessons',
      queryParameters: {'year': w.year, 'month': w.month, 'week': w.week},
    ),
    (data) =>
        (((data as Map<String, dynamic>?)?['items'] as List<dynamic>?) ??
                const [])
            .map(
              (e) => ((e as Map<String, dynamic>)['lessonId'] as num).toInt(),
            )
            .toList(),
  );

  Future<ParentLessonDetail> lesson(int studentId, int lessonId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/lessons/$lessonId',
    ),
    (data) => ParentLessonDetail.fromJson(data! as Map<String, dynamic>),
  );

  /// 그 주의 클리닉 출결. null 은 확정 전이다.
  Future<List<DayStatus?>> clinics(int studentId, ReportWeek w) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/clinics',
      queryParameters: {'year': w.year, 'month': w.month, 'week': w.week},
    ),
    (data) => [
      for (final e in (data as List<dynamic>?) ?? const [])
        switch ((e as Map<String, dynamic>)['attendStatus']) {
          final String s => DayStatus.parse(s),
          _ => null,
        },
    ],
  );

  Future<ScoreData> scores(int studentId) => _scores.scores(studentId);
}

/// P-6. 매개변수는 (자녀, 주)다.
class ParentReportController
    extends ParamController<(int, ReportWeek), WeeklyReport> {
  ParentReportController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final ParentReportRepository _repository;

  @override
  Future<WeeklyReport> fetch((int, ReportWeek) param) async {
    final (id, week) = param;
    final (head, scores) = await both(
      all3(
        _repository.header(id),
        _repository.lessonIds(id, week),
        _repository.clinics(id, week),
      ),
      _repository.scores(id),
    );
    final ((name, rooms), ids, clinics) = head;
    // 목록에는 수업 내용이 없다. 그 주 수업마다 상세를 한 번씩 부른다(보통 1~2회).
    // Future.wait 는 처음 실패를 그대로 던진다(서버 문구가 산다).
    final lessons = await Future.wait([
      for (final lessonId in ids) _repository.lesson(id, lessonId),
    ]);
    return buildWeeklyReport(
      week: week,
      name: name,
      classRooms: rooms,
      lessons: lessons,
      clinics: clinics,
      scores: scores,
    );
  }
}
