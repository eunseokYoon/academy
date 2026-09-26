/// 학생 홈(S-1)과 학부모 홈(P-1)이 **함께 쓰는** 표시용 글자.
///
/// 원래 `student_home_page.dart` 안의 private 함수였다. 학부모 홈이 같은
/// 것을 필요로 해서 여기로 옮겼다 — **두 화면에 한 벌씩 두지 마라.**
/// 웹의 두 페이지가 같은 글자를 그리는데(「아이 폰에는 다르게 나온다」는
/// 문의를 막으려고), 앱에서 두 벌이 되면 한쪽만 고쳐져 갈라진다.
library;

/// 요일 한 글자. 1=월~7=일(ISO-8601). 웹 `shared/date.ts` 의 `DAY_LABELS` 와
/// 같은 기준이고 `DateTime.weekday` 를 그대로 인덱스로 쓴다.
const List<String> _dayLabels = ['월', '화', '수', '목', '금', '토', '일'];

/// 「2026년 9월 21일 월요일」. 웹 `shared/date.ts` 의 `todayLabel`.
///
/// 장식이 아니라 **기준점**이다 — 아래의 D-3·마감 8일 지남이 전부 오늘을
/// 기준으로 센 값이라, 오늘이 안 적혀 있으면 기기 시계가 틀어졌을 때
/// 알아챌 방법이 없다. **표시용이라 `DateTime.now()` 를 써도 된다.**
/// 금지된 것은 D-day 계산이다(서버가 준다).
///
/// `now` 는 테스트용이다. 화면은 넘기지 않는다.
String todayLabel({DateTime? now}) {
  now ??= DateTime.now();
  return '${now.year}년 ${now.month}월 ${now.day}일 '
      '${_dayLabels[now.weekday - 1]}요일';
}

/// 「D-2」 · 「오늘」. **서버가 준 dDay 를 그대로 쓴다.**
/// 서버는 음수를 안 내려준다.
///
/// 시험 칸은 이것을 쓰지 않고 `D-${dDay}` 를 그대로 쓴다(오늘이면 「D-0」).
/// 웹 두 홈이 그렇게 되어 있고 웹이 정본이다 — 「정리」하지 마라.
String dDayLabel(int dDay) => dDay == 0 ? '오늘' : 'D-$dDay';

/// 「09/21」 또는 「09/21 19:00」 — 연도를 뺀다. 360px 에서 잘린다.
/// **서버 문자열을 자를 뿐 날짜를 새로 만들지 않는다.** `time` 이 null 이면
/// 날짜만이다 — 시각을 지어내 채우지 마라.
String dateLabel(String date, String? time) {
  final short = date.substring(5).replaceFirst('-', '/');
  return time == null ? short : '$short $time';
}

/// 공지 줄의 「09/15」. `publishedAt` 에서 잘라낸다 — 웹도 목록에서 자른다.
String shortDate(String publishedAt) =>
    publishedAt.substring(5, 10).replaceFirst('-', '/');

/// 웹 `shared/score/types.ts` 의 `EXAM_TYPE_LABELS`. 백엔드 `ExamType` 은
/// MIDTERM·FINAL 둘뿐이다 — 모르는 값이 오면 서버 문자열을 그대로 보여준다.
String examLabel(String examType) => switch (examType) {
  'MIDTERM' => '중간고사',
  'FINAL' => '기말고사',
  _ => examType,
};

/// 지면 시험 칸의 sub 「10.01 시작」. 연도를 뺀다 — 360px 에서
/// 「2026.10.12 시작」은 잘린다.
String examStartShort(String startDate) =>
    '${startDate.substring(5).replaceFirst('-', '.')} 시작';
