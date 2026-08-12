/** NOT_STARTED는 서버에 행이 없는 상태다. */
export type OnlineTestTakeStatus = "NOT_STARTED" | "IN_PROGRESS" | "SUBMITTED";

export const TAKE_STATUS_LABELS: Record<OnlineTestTakeStatus, string> = {
  NOT_STARTED: "미응시",
  IN_PROGRESS: "작성 중",
  SUBMITTED: "제출 완료",
};

export interface QuestionResult {
  questionNo: number;
  /** null이면 미체크다. 오답으로 처리되고 감점은 없다. */
  chosen: number | null;
  correct: number;
  isCorrect: boolean;
}

/** 제출 후에만 받는 응답이다. 응시 화면에는 정답이 내려오지 않는다. */
export interface OnlineTestResult {
  testId: number;
  title: string;
  score: number;
  correctCount: number;
  questionCount: number;
  submittedAt: string;
  answerFileUrl: string | null;
  results: QuestionResult[];
}
