import type { HomeworkResult } from "./types";

/**
 * 셀 하나의 표시 문자열. 선생님·학생·학부모 세 화면이 같은 함수를 쓴다.
 *
 * <p>result가 null이면 "미채점"이다. 0%가 아니다 — 선생님이 아직 안 채운 칸이라
 * 회색으로 보여야 한다. 여기서 null을 0으로 접으면 학부모가 "하나도 안 했다"로 읽는다.
 */
export function gradeLabel(
  result: HomeworkResult | null,
  completionRate: number | null,
  resolvedByResubmission: boolean,
): string {
  if (result === null) return "미채점";
  if (result === "DONE") return resolvedByResubmission ? "완료 (재제출)" : "완료";
  if (result === "PARTIAL") return `일부 ${completionRate ?? 0}%`;
  return "미완료";
}

/** Badge의 tone 값이다. 새 값을 만들지 마라 — Badge가 아는 색만 있다. */
export function gradeTone(
  result: HomeworkResult | null,
): "ok" | "warn" | "danger" | "neutral" {
  if (result === "DONE") return "ok";
  if (result === "PARTIAL") return "warn";
  if (result === "NOT_DONE") return "danger";
  return "neutral";
}
