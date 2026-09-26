/// 학생(S-7)·학부모(P-4)·주간 레포트(P-6)가 같이 받는 성적 응답. 백엔드
/// `StudentScoreResponse` 가 정본이고 웹 `shared/score/types.ts` 와 같은 모양이다.
///
/// **정기고사는 여기 없다**(선생님 전용, 7-1). 등수·백분위·반 평균도 없다.
/// 필드를 더해 채우지 마라.
library;

/// 서버가 정한 그래프 종류(7-5). **`testType` 으로 다시 분기하지 마라** —
/// 성적 목록과 주간 레포트가 갈라진다. 모르는 값은 그래프 없음으로 둔다.
enum ChartKind {
  none,
  bar,
  splitBar;

  static ChartKind parse(String? wire) => switch (wire) {
    'BAR' => ChartKind.bar,
    'SPLIT_BAR' => ChartKind.splitBar,
    _ => ChartKind.none,
  };
}

/// 한 주차 한 종류. 종류마다 채워지는 칸이 다르다 — 단어·실전은 `correctCount`,
/// 클리닉은 내부·외부, 리뷰는 `result` 뿐이다.
///
/// **`accuracy` 는 받지 않는다**(2026-09-10). 서버가 항상 null 을 보내고,
/// 정답률은 어느 화면에도 없다. 모델에 두면 누군가 그린다.
class ScoreItem {
  const ScoreItem({
    required this.year,
    required this.month,
    required this.week,
    required this.weekLabel,
    this.correctCount,
    this.totalCount,
    this.internalCorrect,
    this.internalTotal,
    this.externalCorrect,
    this.externalTotal,
    this.result,
    this.retestPassed = false,
    this.retestScheduled = false,
  });

  final int year;
  final int month;
  final int week;

  /// 「8월 2주」. 서버가 만든다(9-2) — 앱에서 날짜로 주차를 세지 마라.
  final String weekLabel;
  final int? correctCount;
  final int? totalCount;
  final int? internalCorrect;
  final int? internalTotal;
  final int? externalCorrect;
  final int? externalTotal;

  /// `PASS`·`FAIL`. null 이면 판정 칸이 없는 종류다.
  final String? result;
  final bool retestPassed;
  final bool retestScheduled;

  /// 주차 하나를 크기 비교가 되는 정수로 편다. 2026년 8월 2주 → 20260802.
  int get key => weekKey(year, month, week);

  factory ScoreItem.fromJson(Map<String, dynamic> json) {
    int? n(String k) => (json[k] as num?)?.toInt();
    return ScoreItem(
      year: (json['year'] as num).toInt(),
      month: (json['month'] as num).toInt(),
      week: (json['week'] as num).toInt(),
      weekLabel: json['weekLabel'] as String,
      correctCount: n('correctCount'),
      totalCount: n('totalCount'),
      internalCorrect: n('internalCorrect'),
      internalTotal: n('internalTotal'),
      externalCorrect: n('externalCorrect'),
      externalTotal: n('externalTotal'),
      result: json['result'] as String?,
      // 자바 boolean 이라 기본값이 허용된다(14-3).
      retestPassed: json['retestPassed'] as bool? ?? false,
      retestScheduled: json['retestScheduled'] as bool? ?? false,
    );
  }
}

int weekKey(int year, int month, int week) => year * 10000 + month * 100 + week;

class ScoreSection {
  const ScoreSection({
    required this.testType,
    required this.label,
    required this.chartKind,
    required this.items,
  });

  final String testType;
  final String label;
  final ChartKind chartKind;

  /// **year·month·week 오름차순**이다. 그래프가 그대로 쓰고, 목록은 화면에서
  /// 뒤집어 그린다. 다시 정렬하지 마라.
  final List<ScoreItem> items;

  factory ScoreSection.fromJson(Map<String, dynamic> json) => ScoreSection(
    testType: json['testType'] as String,
    label: json['label'] as String,
    chartKind: ChartKind.parse(json['chartKind'] as String?),
    items: ((json['items'] as List<dynamic>?) ?? const [])
        .map((e) => ScoreItem.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

class RetestNotice {
  const RetestNotice({
    required this.testType,
    required this.weekLabel,
    required this.label,
  });

  final String testType;
  final String weekLabel;

  /// 서버가 만든 문장 그대로다.
  final String label;

  factory RetestNotice.fromJson(Map<String, dynamic> json) => RetestNotice(
    testType: json['testType'] as String,
    weekLabel: json['weekLabel'] as String,
    label: json['label'] as String,
  );
}

class ScoreData {
  const ScoreData({required this.retestScheduled, required this.sections});

  /// 최신 주차부터 내림차순이다.
  final List<RetestNotice> retestScheduled;

  /// 데이터가 있는 종류만 온다. 빈 구획은 오지 않는다 — 앱에서 거르지 마라.
  final List<ScoreSection> sections;

  factory ScoreData.fromJson(Map<String, dynamic> json) => ScoreData(
    retestScheduled: ((json['retestScheduled'] as List<dynamic>?) ?? const [])
        .map((e) => RetestNotice.fromJson(e as Map<String, dynamic>))
        .toList(),
    sections: ((json['sections'] as List<dynamic>?) ?? const [])
        .map((e) => ScoreSection.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

/// 시험 일정 하나. 백엔드 `StudentExamScheduleResponse`.
class ExamSchedule {
  const ExamSchedule({
    required this.examType,
    required this.startDate,
    required this.endDate,
    required this.scopeNote,
    required this.dDay,
  });

  final String examType;
  final String startDate;
  final String endDate;
  final String? scopeNote;

  /// **서버가 센 값이다**(14-2). 음수면 이미 지난 시험이다.
  final int dDay;

  factory ExamSchedule.fromJson(Map<String, dynamic> json) => ExamSchedule(
    examType: json['examType'] as String,
    startDate: json['startDate'] as String,
    endDate: json['endDate'] as String,
    scopeNote: json['scopeNote'] as String?,
    dDay: (json['dDay'] as num).toInt(),
  );
}

List<ExamSchedule> examSchedulesFromJson(Object? data) =>
    ((data as List<dynamic>?) ?? const [])
        .map((e) => ExamSchedule.fromJson(e as Map<String, dynamic>))
        .toList();
