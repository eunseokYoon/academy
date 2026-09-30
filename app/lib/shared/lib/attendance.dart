import 'package:flutter/painting.dart';

import '../../core/theme/app_colors.dart';

/// 출석 공용 모델과 표시 규칙. S-6(학생 출석) · P-2(학부모 일정)가 같이 쓴다.
/// 웹 정본은 `frontend/src/shared/attendance/types.ts` 다.
///
/// **PENDING 은 출석이 아니라 「아직 확정되지 않은 날」이다**(CLAUDE.md 5).
/// 미래 수업일도 PENDING 이라, 이 구분이 없으면 아직 오지 않은 날이 초록으로 보인다.
enum DayStatus {
  present('PRESENT', '출석'),
  late('LATE', '지각'),
  absent('ABSENT', '결석'),
  sick('SICK', '병결'),
  excused('EXCUSED', '공결'),

  /// 대체 등원. **출석으로 친다**(5-1) — 요약의 「출석」 칸이 `present + makeup`
  /// 이고, 어느 날이 대체 등원이었는지는 칩의 글씨가 보여준다.
  makeup('MAKEUP', '대체 등원'),

  /// 온라인(2026-09-29). 수업에 못 오고 영상으로 들었다. **출석으로 친다.**
  /// 결석인 학생이 영상을 80% 이상 보면 서버가 이 값으로 바꾼다.
  online('ONLINE', '온라인'),
  pending('PENDING', '미확인');

  const DayStatus(this.wire, this.label);

  final String wire;

  /// 칩과 범례에 **글씨로** 적는다 — 색약 사용자에게 통하는 건 글씨뿐이다.
  final String label;

  /// 클리닉의 `attendStatus` 는 확정 전이면 null 이다 — 그게 곧 PENDING 이다
  /// (웹도 호출부에서 `?? "PENDING"` 으로 바꾼다).
  ///
  /// 모르는 값도 PENDING 으로 그린다. 서버가 상태를 늘렸는데 앱이 옛 판이면
  /// 캘린더 전체가 죽는 것보다 그 칸이 회색으로 남는 편이 낫다 — 회색은
  /// 「확정 전」이지 출석도 결석도 아니다.
  static DayStatus parse(String? wire) => DayStatus.values.firstWhere(
    (s) => s.wire == wire,
    orElse: () => DayStatus.pending,
  );
}

/// 칩 한 개의 색. 웹 `DAY_STATUS_STYLE` 의 `bg-* text-* ring-*` 셋이다.
///
/// **브랜드 남색으로 바꾸지 마라.** 포인트가 파랑으로 옮겨간 덕분에 빨강이 이
/// 표에서 「결석」 하나만 뜻하게 됐다.
class DayStatusStyle {
  const DayStatusStyle(this.background, this.foreground, this.ring);

  final Color background;
  final Color foreground;
  final Color ring;
}

DayStatusStyle dayStatusStyle(DayStatus status) => switch (status) {
  DayStatus.present => const DayStatusStyle(
    AppColors.emerald100,
    AppColors.emerald800,
    AppColors.emerald200,
  ),
  DayStatus.late => const DayStatusStyle(
    AppColors.amber100,
    AppColors.amber800,
    AppColors.amber200,
  ),
  DayStatus.absent => const DayStatusStyle(
    AppColors.red100,
    AppColors.red800,
    AppColors.red200,
  ),
  DayStatus.sick => const DayStatusStyle(
    AppColors.sky100,
    AppColors.sky800,
    AppColors.sky200,
  ),
  DayStatus.excused => const DayStatusStyle(
    AppColors.slate200,
    AppColors.slate700,
    AppColors.slate300,
  ),
  DayStatus.makeup => const DayStatusStyle(
    AppColors.teal100,
    AppColors.teal800,
    AppColors.teal200,
  ),
  DayStatus.online => const DayStatusStyle(
    AppColors.violet100,
    AppColors.violet800,
    AppColors.violet200,
  ),
  DayStatus.pending => const DayStatusStyle(
    AppColors.slate100,
    AppColors.slate400,
    AppColors.slate200,
  ),
};

/// 백엔드 `AttendanceSummaryResponse`. 전부 자바 `int` 라 기본값 0 이 허용된다(14-3).
class AttendanceSummary {
  const AttendanceSummary({
    required this.present,
    required this.late,
    required this.absent,
    required this.sick,
    required this.excused,
    required this.makeup,
    this.online = 0,
  });

  final int present;
  final int late;
  final int absent;
  final int sick;
  final int excused;
  final int makeup;

  /// 온라인(2026-09-29). 출석으로 친다.
  final int online;

  /// 요약의 「출석」 칸. **`present` 만 쓰지 마라** — 대체 등원·온라인 날이 출석·지각·
  /// 결석 어디에도 안 잡힌다(5-1, 14-11).
  int get attended => present + makeup + online;

  factory AttendanceSummary.fromJson(Map<String, dynamic> json) {
    int n(String key) => (json[key] as num?)?.toInt() ?? 0;
    return AttendanceSummary(
      present: n('present'),
      late: n('late'),
      absent: n('absent'),
      sick: n('sick'),
      excused: n('excused'),
      makeup: n('makeup'),
      online: n('online'),
    );
  }
}

/// 수업 하루. 숙제 완료율 띠는 없앴다(5-2) — `homeworkRate` 는 읽지 않는다.
class AttendanceDay {
  const AttendanceDay({required this.date, required this.status});

  /// `yyyy-MM-dd`. 서버 문자열 그대로다 — 파싱하지 않고 키로 쓴다.
  final String date;
  final DayStatus status;

  factory AttendanceDay.fromJson(Map<String, dynamic> json) => AttendanceDay(
    date: json['date'] as String,
    status: DayStatus.parse(json['status'] as String?),
  );
}

/// 백엔드 `AttendanceCalendarResponse`.
class AttendanceMonth {
  const AttendanceMonth({
    required this.year,
    required this.month,
    required this.summary,
    required this.homeworkCompletionRate,
    required this.days,
  });

  final int year;
  final int month;
  final AttendanceSummary summary;

  /// 숙제가 없던 달은 null 이다. **0 으로 채우지 마라** — 0% 로 보이면 「전부
  /// 안 했다」로 읽힌다. null 이면 카드를 통째로 뺀다.
  final int? homeworkCompletionRate;
  final List<AttendanceDay> days;

  factory AttendanceMonth.fromJson(Map<String, dynamic> json) =>
      AttendanceMonth(
        year: (json['year'] as num).toInt(),
        month: (json['month'] as num).toInt(),
        summary: AttendanceSummary.fromJson(
          json['summary'] as Map<String, dynamic>,
        ),
        homeworkCompletionRate: (json['homeworkCompletionRate'] as num?)
            ?.toInt(),
        // 새 배열 필드는 `?? []` 로 받는다(7-3).
        days: ((json['days'] as List<dynamic>?) ?? const [])
            .map((e) => AttendanceDay.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// 캘린더 칸에 들어가는 클리닉 하나. 같은 날 둘 이상일 수 있다.
class ClinicEntry {
  const ClinicEntry({required this.date, required this.status});

  final String date;

  /// 확정 전이면 PENDING 이다([DayStatus.parse] 가 null 을 그렇게 바꾼다).
  final DayStatus status;
}

/// 캘린더 한 달 치 — 수업 출석과 그 달의 내(자녀) 클리닉.
class AttendanceCalendarData {
  const AttendanceCalendarData({required this.month, required this.clinics});

  final AttendanceMonth month;
  final List<ClinicEntry> clinics;
}

/// 캘린더의 달 하나. 화면이 들고 있다가 ‹ › 로 옮긴다.
///
/// 월 계산만 한다 — 주차를 세지 마라(주차의 정본은 서버 `MonthWeeks`, 9-2).
typedef YearMonth = ({int year, int month});

YearMonth shiftMonth(YearMonth ym, int delta) {
  // DateTime 이 13월·0월을 알아서 넘긴다. 시각이 없는 날짜 계산이라 시간대와 무관하다.
  final d = DateTime(ym.year, ym.month + delta);
  return (year: d.year, month: d.month);
}

String _two(int n) => n.toString().padLeft(2, '0');

/// 그 달의 첫날·마지막 날(`yyyy-MM-dd`). 학생 클리닉 API 가 from·to 로 받는다.
(String, String) monthRange(YearMonth ym) {
  final last = DateTime(ym.year, ym.month + 1, 0).day;
  final prefix = '${ym.year}-${_two(ym.month)}';
  return ('$prefix-01', '$prefix-${_two(last)}');
}

String dateKey(int year, int month, int day) =>
    '$year-${_two(month)}-${_two(day)}';
