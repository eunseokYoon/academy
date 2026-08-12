/**
 * 주차 선택값 유틸.
 *
 * <p><b>여기서 주차를 만드는 건 선택 상자의 초기값뿐이다.</b> 데이터를 주차로 묶는 일은
 * 서버가 한다 — MonthWeeks가 정본이고 응답의 weekLabel이 그 결과다. 화면에서 날짜로
 * 주차를 계산해 데이터를 묶기 시작하면 수업·성적·클리닉이 서로 다른 날을 가리키게 된다.
 */

/** 달 안에서 1일부터 7일씩 끊는다. 서버 MonthWeeks.of와 같은 규칙이다. */
export function currentWeek(date = new Date()): { year: number; month: number; week: number } {
  return {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    week: Math.floor((date.getDate() - 1) / 7) + 1,
  };
}

/**
 * 주차 하나를 크기 비교가 되는 정수로 편다. 2026년 8월 2주 → 20260802.
 *
 * <p>세 값을 따로 비교하면 "달이 넘어갈 때"에서 반드시 틀린다 —
 * 9월 1주가 8월 5주보다 앞선다고 판정된다.
 */
export function weekKey(year: number, month: number, week: number): number {
  return year * 10000 + month * 100 + week;
}
