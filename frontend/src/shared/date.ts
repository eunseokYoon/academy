/**
 * 요일 표기. 학생·학부모·선생님 화면이 모두 쓰기 때문에 shared에 둔다.
 *
 * <p>예전에는 routes/teacher/format.ts에 있었고 학생·학부모 화면이 거기서 가져다 썼다.
 * 화면 하나에 속한 파일을 다른 화면이 참조하는 방향이라 옮겼다.
 */

/** 1=월 ~ 7=일 (ISO-8601). 서버의 dayOfWeek와 같은 기준이다. */
export const DAY_LABELS: Record<number, string> = {
  1: "월",
  2: "화",
  3: "수",
  4: "목",
  5: "금",
  6: "토",
  7: "일",
};

/**
 * 날짜 문자열("2026-08-13")의 요일 한 글자.
 * getDay()는 일요일이 0이라 7로 바꿔 받는다 — 이 보정이 빠지면 일요일이 undefined다.
 */
export function dayLabel(date: string): string {
  return DAY_LABELS[new Date(date).getDay() || 7];
}
