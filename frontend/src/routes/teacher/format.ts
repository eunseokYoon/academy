import type { ClassRoomSchedule } from "./api";

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
 * 반의 수업 시간을 한 줄로. "화 19:00~21:00 · 목 19:00"
 *
 * 화면마다 각자 조립하면 표기가 갈린다. 여기 한 곳에서만 만든다.
 * 정렬은 서버가 요일 오름차순으로 내려주므로 여기서 다시 정렬하지 않는다.
 */
export function formatSchedules(schedules: ClassRoomSchedule[]): string {
  if (schedules.length === 0) return "요일 미정";
  return schedules
    .map((schedule) => {
      const day = DAY_LABELS[schedule.dayOfWeek];
      const start = schedule.startTime.slice(0, 5);
      return schedule.endTime ? `${day} ${start}~${schedule.endTime.slice(0, 5)}` : `${day} ${start}`;
    })
    .join(" · ");
}

/** 요일만. 수업 일괄 생성 안내처럼 시각이 필요 없는 자리에 쓴다. "화·목" */
export function formatScheduleDays(schedules: ClassRoomSchedule[]): string {
  return schedules.map((schedule) => DAY_LABELS[schedule.dayOfWeek]).join("·");
}

export function formatWeek(year: number, month: number, week: number): string {
  return `${year}년 ${month}월 ${week}주차`;
}

export function today(): string {
  return new Date().toISOString().slice(0, 10);
}
