/// 숙제 화면들(S-1 홈 · S-2 · S-3/4 · P-3)이 **함께 쓰는** 표시용 글자.
///
/// 웹 `shared/homework/types.ts`·`grade.ts`·`lessonDay.ts` 의 옮김이다. 웹이
/// 세 화면(선생님·학생·학부모)에 같은 함수를 쓰는 이유가 「아이 폰에는 다르게
/// 나온다」를 막는 것이라, 앱에서도 **화면마다 한 벌씩 두지 마라.**
library;

import '../widgets/app_badge.dart';

/// 남은 시간. 웹 `remainingLabel` **그대로**다.
///
/// **서버가 내려준 분 단위 값으로만 만든다** — 기기 시계로 다시 계산하면
/// 사람마다 다른 값이 보인다(CLAUDE.md 14-2). 경계는 60 과 60*24 이고
/// `Math.floor` 는 정수 나눗셈이다.
String remainingLabel(int minutes) {
  if (minutes < 0) {
    final passed = -minutes;
    if (passed < 60) return '마감 $passed분 지남';
    if (passed < 60 * 24) return '마감 ${passed ~/ 60}시간 지남';
    return '마감 ${passed ~/ (60 * 24)}일 지남';
  }
  if (minutes < 60) return '$minutes분 남음';
  if (minutes < 60 * 24) return '${minutes ~/ 60}시간 남음';
  return '${minutes ~/ (60 * 24)}일 남음';
}

/// 「9월 21일 21:00」. 웹 `formatDueAt`.
///
/// **한국 시각으로 명시 변환한다 — `.toLocal()` 을 쓰지 마라**(CLAUDE.md
/// 14-10). `DateTime.parse` 는 `+09:00` 을 UTC 로 돌려주고, `.toLocal()` 은
/// **기기 시간대**를 따른다 — 해외에 나간 학생·UTC 로 도는 테스트 러너에서
/// 마감이 9시간 어긋난다. 한국은 서머타임이 없어서 `+9h` 가 정확하다.
String formatDueAt(String iso) {
  final at = DateTime.parse(iso).toUtc().add(const Duration(hours: 9));
  final hour = at.hour.toString().padLeft(2, '0');
  final minute = at.minute.toString().padLeft(2, '0');
  return '${at.month}월 ${at.day}일 $hour:$minute';
}

/// 온라인 제출 축. 웹 `SUBMISSION_LABELS`.
///
/// **채점축(`result`)과 독립이다** — GRID 에서 ⭕를 받은 학생은 온라인
/// 제출을 안 하므로 이 값이 영원히 `NOT_SUBMITTED` 다(CLAUDE.md 4-2).
/// GRID 의 상태를 이것으로 그리지 마라.
String submissionLabel(String status) => switch (status) {
  'SUBMITTED' => '제출 완료',
  'NOT_SUBMITTED' => '미제출',
  _ => status,
};

/// 채점 칸 하나의 글자. 웹 `gradeLabel`.
///
/// **`result` 가 null 이면 「미채점」이다. 0% 가 아니다** — 선생님이 아직 안
/// 채운 칸이라 회색으로 보여야 한다. 여기서 null 을 0 으로 접으면 학부모가
/// 「하나도 안 했다」로 읽는다(CLAUDE.md 4-2 · 14-3).
String gradeLabel(
  String? result,
  int? completionRate,
  bool resolvedByResubmission,
) {
  if (result == null) return '미채점';
  if (result == 'DONE') return resolvedByResubmission ? '완료 (재제출)' : '완료';
  // 웹도 `?? 0` 이다. PARTIAL 은 DB 가 1~99 를 강제하므로 null 이 올 수 없다.
  if (result == 'PARTIAL') return '일부 ${completionRate ?? 0}%';
  return '미완료';
}

/// 채점 배지의 색. 웹 `gradeTone`.
BadgeTone gradeTone(String? result) => switch (result) {
  'DONE' => BadgeTone.ok,
  'PARTIAL' => BadgeTone.warn,
  'NOT_DONE' => BadgeTone.danger,
  _ => BadgeTone.neutral,
};

/// 「2026-08-13」 → 「8월 13일 수업」. 해가 다르면 연도를 붙인다 — 해가
/// 바뀌면 「8월 13일」만으로는 올해 것인지 알 수 없다.
///
/// **`DateTime.parse` 하지 않는다.** 서버의 날짜 문자열을 자를 뿐이다 —
/// 파싱하면 시간대에 따라 하루가 어긋날 수 있다(웹이 겪었다). `thisYear` 는
/// 표시용이라 기기 시계를 써도 된다(14-2 의 예외와 같다). 테스트만 넘긴다.
String lessonDayLabel(String lessonDate, {int? thisYear}) {
  final parts = lessonDate.split('-').map(int.parse).toList();
  final label = '${parts[1]}월 ${parts[2]}일 수업';
  return parts[0] == (thisYear ?? DateTime.now().year)
      ? label
      : '${parts[0]}년 $label';
}

/// 칩에 들어가는 짧은 라벨. 「8/13」
String lessonDayShort(String lessonDate) {
  final parts = lessonDate.split('-').map(int.parse).toList();
  return '${parts[1]}/${parts[2]}';
}

/// 수업일 한 묶음. [lessonDate] 가 null 이면 수업에 안 붙은 ONLINE 숙제
/// (방학 과제 등)다.
class LessonDayGroup<T> {
  const LessonDayGroup({
    required this.lessonDate,
    required this.label,
    required this.items,
  });

  final String? lessonDate;
  final String label;
  final List<T> items;
}

/// 숙제 목록을 **수업일**로 묶는다. 웹 `groupByLessonDay`. S-2 와 P-3 가 같이 쓴다.
///
/// 최근 수업일이 위로 온다. **수업일 없는 것은 항상 맨 아래 한 덩어리**다 —
/// 날짜가 없어 어느 자리에도 끼울 수 없고, 흩어 두면 목록 중간에 이유 없이
/// 나타난다. ISO 날짜는 문자열 비교가 곧 날짜 비교라 파싱하지 않는다.
///
/// **주차를 계산하지 마라** — 「몇 월 몇 주차」의 정본은 서버의 `MonthWeeks`
/// 다(CLAUDE.md 9-2). 묶는 키는 언제나 `lessonDate` 그대로다.
List<LessonDayGroup<T>> groupByLessonDay<T>(
  List<T> items,
  String? Function(T) lessonDateOf, {
  int? thisYear,
}) {
  final byDate = <String, List<T>>{};
  final undated = <T>[];
  for (final item in items) {
    final date = lessonDateOf(item);
    if (date == null) {
      undated.add(item);
    } else {
      (byDate[date] ??= []).add(item);
    }
  }
  final dates = byDate.keys.toList()..sort((a, b) => b.compareTo(a));
  return [
    for (final date in dates)
      LessonDayGroup(
        lessonDate: date,
        label: lessonDayLabel(date, thisYear: thisYear),
        items: byDate[date]!,
      ),
    if (undated.isNotEmpty)
      LessonDayGroup<T>(lessonDate: null, label: '수업일 없음', items: undated),
  ];
}
