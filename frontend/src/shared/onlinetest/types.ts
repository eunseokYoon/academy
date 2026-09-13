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
  /** <b>쓰지 마라</b>(2026-09-10). 환산 점수를 화면에 보이지 않기로 했다. */
  score: number;
  correctCount: number;
  questionCount: number;
  /** 앞 N문항이 내부지문. null이면 나누지 않은 테스트다. */
  internalQuestionCount: number | null;
  /** internalQuestionCount가 null이면 null이다. */
  internalCorrect: number | null;
  externalCorrect: number | null;
  submittedAt: string;
  answerFileUrl: string | null;
  results: QuestionResult[];
}
