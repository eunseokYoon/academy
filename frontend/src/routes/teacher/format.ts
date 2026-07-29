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

export function formatWeek(year: number, month: number, week: number): string {
  return `${year}년 ${month}월 ${week}주차`;
}

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}
