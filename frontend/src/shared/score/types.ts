export type ScoreType = "WORD" | "INTERNAL" | "MOCK";
export type ExamType = "MIDTERM" | "FINAL";

export const SCORE_TYPE_LABELS: Record<ScoreType, string> = {
  WORD: "단어 테스트",
  INTERNAL: "내신",
  MOCK: "모의고사",
};

export const EXAM_TYPE_LABELS: Record<ExamType, string> = {
  MIDTERM: "중간고사",
  FINAL: "기말고사",
};

/**
 * 주차별 단어 테스트 한 점. label은 서버가 만든 값을 그대로 쓴다.
 * 프론트에서 조립하면 표기가 화면마다 갈린다.
 */
export interface WordPoint {
  year: number;
  month: number;
  week: number;
  label: string;
  /** 100점 만점 환산값이다. */
  score: number;
  examName: string;
  examDate: string;
}

/** 내신·모의 한 줄. examDate 내림차순으로 내려온다. */
export interface ScoreItem {
  scoreId: number;
  examName: string;
  subject: string;
  rawScore: number | null;
  gradeLevel: number | null;
  examDate: string;
  memo: string | null;
}

/**
 * S-7 · P-4 응답. 등수·백분위·반 평균은 어디에도 없다 — 서버가 내려주지 않는다.
 *
 * <p>word.points는 이미 year·month·week 오름차순이다. <b>다시 정렬하지 마라.</b>
 * 시험을 안 본 주는 배열에 없으니 빈 점을 채워 넣지도 마라 — 선이 0으로 떨어진다.
 */
export interface ScoreChart {
  word: { unit: string; points: WordPoint[] };
  internal: ScoreItem[];
  mock: ScoreItem[];
}

export interface StudentExamSchedule {
  examType: ExamType;
  startDate: string;
  endDate: string;
  scopeNote: string | null;
  /** 음수면 이미 지난 시험이다. */
  dDay: number;
}
