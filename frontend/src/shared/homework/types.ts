export type SubmissionStatus = "NOT_SUBMITTED" | "SUBMITTED" | "CHECKED";

export const SUBMISSION_LABELS: Record<SubmissionStatus, string> = {
  NOT_SUBMITTED: "미제출",
  SUBMITTED: "확인 대기",
  CHECKED: "확인 완료",
};

export interface HomeworkCounts {
  total: number;
  notSubmitted: number;
  submitted: number;
  checked: number;
}

/**
 * 남은 시간은 서버가 내려준 분 단위 값으로만 만든다.
 * 클라이언트 시계로 다시 계산하면 사람마다 다른 값이 보인다.
 */
export function remainingLabel(minutes: number): string {
  if (minutes < 0) {
    const passed = -minutes;
    if (passed < 60) return `마감 ${passed}분 지남`;
    if (passed < 60 * 24) return `마감 ${Math.floor(passed / 60)}시간 지남`;
    return `마감 ${Math.floor(passed / (60 * 24))}일 지남`;
  }
  if (minutes < 60) return `${minutes}분 남음`;
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 남음`;
  return `${Math.floor(minutes / (60 * 24))}일 남음`;
}

export function formatDueAt(dueAt: string): string {
  const date = new Date(dueAt);
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${String(date.getHours()).padStart(2, "0")}:${String(
    date.getMinutes(),
  ).padStart(2, "0")}`;
}
