/**
 * 숙제 목록을 <b>수업일</b>로 묶는다. S-2(학생)와 P-3(학부모)가 같이 쓴다.
 *
 * <p>여기서 하는 건 날짜 문자열을 자르고 붙이는 것뿐이다. <b>주차를 계산하지 마라</b> —
 * "몇 월 몇 주차"의 정본은 서버의 MonthWeeks이고, 화면에서 다시 세면 클리닉·성적이
 * 가리키는 주와 갈라진다. 묶는 키는 언제나 lessonDate 그대로다.
 */

/**
 * "2026-08-13" → "8월 13일 수업".
 *
 * <p><b>new Date로 파싱하지 않는다.</b> "2026-08-13"은 UTC 자정으로 읽혀서
 * Asia/Seoul에서 하루 앞 날짜가 나온다 — 8월 13일 수업이 8월 12일 그룹에 앉는다.
 */
export function lessonDayLabel(lessonDate: string, thisYear = new Date().getFullYear()): string {
  const [year, month, day] = lessonDate.split("-").map(Number);
  const label = `${month}월 ${day}일 수업`;
  // 해가 바뀌면 "8월 13일"만으로는 올해 것인지 알 수 없다
  return year === thisYear ? label : `${year}년 ${label}`;
}

/** 칩에 들어가는 짧은 라벨. "8/13" */
export function lessonDayShort(lessonDate: string): string {
  const [, month, day] = lessonDate.split("-").map(Number);
  return `${month}/${day}`;
}

export interface LessonDayGroup<T> {
  /** null이면 수업에 안 붙은 ONLINE 숙제(방학 과제 등)다. */
  lessonDate: string | null;
  label: string;
  items: T[];
}

/**
 * 최근 수업일이 위로 온다. <b>수업일 없는 것은 항상 맨 아래 한 덩어리</b>다 —
 * 날짜가 없어 어느 자리에도 끼울 수 없고, 흩어 두면 목록 중간에 이유 없이 나타난다.
 *
 * <p>ISO 날짜는 문자열 비교가 곧 날짜 비교라 Date로 바꾸지 않는다.
 */
export function groupByLessonDay<T extends { lessonDate: string | null }>(
  items: T[],
): LessonDayGroup<T>[] {
  const byDate = new Map<string, T[]>();
  const undated: T[] = [];

  for (const item of items) {
    if (item.lessonDate === null) {
      undated.push(item);
      continue;
    }
    const bucket = byDate.get(item.lessonDate);
    if (bucket) bucket.push(item);
    else byDate.set(item.lessonDate, [item]);
  }

  const groups: LessonDayGroup<T>[] = [...byDate.entries()]
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([lessonDate, groupItems]) => ({
      lessonDate,
      label: lessonDayLabel(lessonDate),
      items: groupItems,
    }));

  if (undated.length > 0) {
    groups.push({ lessonDate: null, label: "수업일 없음", items: undated });
  }
  return groups;
}
