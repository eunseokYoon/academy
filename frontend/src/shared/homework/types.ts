/**
 * 숙제의 종류. GRID는 반 × 수업일 그리드의 열 하나로, 오프라인으로 채점하고
 * 🔺❌를 받은 학생만 온라인으로 다시 낸다. ONLINE은 처음부터 반 전원이 내는 기존 방식이다.
 */
export type HomeworkKind = "GRID" | "ONLINE";

/**
 * 오프라인 채점 결과. <b>null이 "아직 채점 안 함"</b>이고 회색 "미채점"으로 보인다.
 * 퍼센트는 PARTIAL에만 붙는다 — DONE은 100, NOT_DONE은 0이라 숫자가 필요 없다.
 */
export type HomeworkResult = "DONE" | "PARTIAL" | "NOT_DONE";

/**
 * 온라인 제출 축. 채점축(HomeworkResult)과 독립이다 —
 * GRID에서 ⭕를 받은 학생은 온라인 제출을 하지 않으므로 여기는 계속 NOT_SUBMITTED다.
 */
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
