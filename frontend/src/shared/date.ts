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

/**
 * 오늘 한 줄. "2026년 8월 12일 수요일".
 *
 * <p>홈 지면에서 인사말 위에 얹는다. 장식이 아니라 <b>기준점</b>이다 —
 * 그 아래 D-3·마감 8일 지남이 전부 오늘을 기준으로 센 값이라, 오늘이 안 적혀 있으면
 * 기기 시계가 틀어졌을 때 학생이 알아챌 방법이 없다.
 */
export function todayLabel(now: Date = new Date()): string {
  return `${now.getFullYear()}년 ${now.getMonth() + 1}월 ${now.getDate()}일 ${
    DAY_LABELS[now.getDay() || 7]
  }요일`;
}

/**
 * 화면을 열었을 때 기본으로 고를 주차. 달 안에서 1일부터 7일씩 끊는다 —
 * 서버의 MonthWeeks와 같은 규칙이다.
 *
 * <p><b>이 값으로 데이터를 묶지 마라.</b> 주차 라벨과 날짜 범위는 서버가 정본이고
 * (CLAUDE.md 9-2), 여기 있는 건 "셀렉트의 초깃값"뿐이다. T-5·T-13이 같이 쓴다 —
 * 화면마다 복사하면 기본 선택이 서로 달라진다.
 */
export function currentWeekOfMonth(now: Date = new Date()): number {
  return Math.floor((now.getDate() - 1) / 7) + 1;
}
