export type WeeklyTestType = "WORD" | "REVIEW" | "PRACTICE" | "CLINIC";
export type TestResult = "PASS" | "FAIL";
export type RegularExamSlot =
  | "S1_MIDTERM" | "S1_FINAL" | "S2_MIDTERM" | "S2_FINAL"
  | "MOCK_MAR" | "MOCK_JUN" | "MOCK_SEP";
export type ExamType = "MIDTERM" | "FINAL";

/**
 * 성적 기입 그리드의 열 순서다. 서버도 이 순서로 4종을 내려준다.
 * 화면마다 다시 배열하지 마라 — 선생님이 매번 다른 자리에서 칸을 찾게 된다.
 */
export const WEEKLY_TEST_TYPES: WeeklyTestType[] = ["WORD", "REVIEW", "PRACTICE", "CLINIC"];

export const WEEKLY_TEST_LABELS: Record<WeeklyTestType, string> = {
  WORD: "단어 테스트",
  REVIEW: "리뷰 테스트",
  PRACTICE: "실전 모의고사",
  CLINIC: "클리닉",
};

/** 정기고사 열 순서. <b>선생님만 보는 화면에서만 쓴다.</b> */
export const REGULAR_EXAM_SLOTS: RegularExamSlot[] = [
  "S1_MIDTERM", "S1_FINAL", "S2_MIDTERM", "S2_FINAL",
  "MOCK_MAR", "MOCK_JUN", "MOCK_SEP",
];

export const REGULAR_EXAM_LABELS: Record<RegularExamSlot, string> = {
  S1_MIDTERM: "1학기 중간",
  S1_FINAL: "1학기 기말",
  S2_MIDTERM: "2학기 중간",
  S2_FINAL: "2학기 기말",
  MOCK_MAR: "3월 모의",
  MOCK_JUN: "6월 모의",
  MOCK_SEP: "9월 모의",
};

export const EXAM_TYPE_LABELS: Record<ExamType, string> = {
  MIDTERM: "중간고사",
  FINAL: "기말고사",
};

// ── 선생님 주차 그리드 ──────────────────────────────────────

export interface WeeklyTestStudentRow {
  studentId: number;
  name: string;
  /** false면 지금은 재원생이 아니지만 그 주차에 성적이 남아 있는 학생이다. */
  enrolled: boolean;
}

export interface WeeklyTestCell {
  studentId: number;
  correctCount: number | null;
  internalCorrect: number | null;
  externalCorrect: number | null;
  result: TestResult | null;
  retestPassed: boolean;
}

export interface WeeklyTestColumn {
  testType: WeeklyTestType;
  totalCount: number | null;
  internalTotal: number | null;
  externalTotal: number | null;
  cells: WeeklyTestCell[];
}

/** tests는 항상 4종이 순서대로 들어 있다. 안 본 시험도 값이 null인 항목으로 온다. */
export interface WeeklyTestGrid {
  classRoomId: number;
  year: number;
  month: number;
  week: number;
  /**
   * 이 응답을 만든 서버 시각. <b>저장할 때 그대로 돌려보내야 한다</b> —
   * 화면을 연 뒤에 온라인 클리닉 테스트로 채워진 칸을 서버가 알아보고 지우지 않는다.
   * 빼먹으면 방금 반영된 성적이 저장 한 번에 조용히 사라진다.
   */
  loadedAt: string;
  students: WeeklyTestStudentRow[];
  tests: WeeklyTestColumn[];
}

export interface WeeklyTestSaveBody {
  classRoomId: number;
  year: number;
  month: number;
  week: number;
  /** 그리드 응답의 loadedAt을 그대로 돌려보낸다. */
  loadedAt: string | null;
  tests: {
    testType: WeeklyTestType;
    totalCount: number | null;
    internalTotal: number | null;
    externalTotal: number | null;
    cells: WeeklyTestCell[];
  }[];
}

// ── 정기고사 (선생님 전용) ──────────────────────────────────

export interface RegularExamGrid {
  classRoomId: number;
  year: number;
  students: WeeklyTestStudentRow[];
  scores: { studentId: number; examSlot: RegularExamSlot; rawScore: number }[];
}

export interface RegularExamSaveBody {
  classRoomId: number;
  year: number;
  /** rawScore가 null이면 그 칸의 행을 삭제한다. */
  scores: { studentId: number; examSlot: RegularExamSlot; rawScore: number | null }[];
}

// ── 학생 · 학부모 (같은 응답을 쓴다) ────────────────────────

/**
 * 한 주차 한 종류의 결과. 종류마다 채워지는 필드가 다르다.
 *
 * <p>accuracy·weekLabel·retestScheduled는 <b>서버가 만든 값</b>이다.
 * 프론트에서 다시 계산하면 화면마다 규칙이 갈린다.
 */
export interface StudentScoreItem {
  year: number;
  month: number;
  week: number;
  weekLabel: string;
  correctCount: number | null;
  totalCount: number | null;
  accuracy: number | null;
  internalCorrect: number | null;
  internalTotal: number | null;
  externalCorrect: number | null;
  externalTotal: number | null;
  result: TestResult | null;
  retestPassed: boolean;
  retestScheduled: boolean;
}

export interface StudentScoreSection {
  testType: WeeklyTestType;
  label: string;
  /** true면 정답률 꺾은선을 그린다. 리뷰(P/F뿐)·클리닉(값이 둘)은 false다. */
  chart: boolean;
  /**
   * <b>year·month·week 오름차순</b>이다. 그래프가 그대로 쓰는 순서이고
   * 목록은 화면에서 뒤집어 그린다. 다시 정렬하지 마라.
   */
  items: StudentScoreItem[];
}

/**
 * S-7 · P-4 응답. 학생과 학부모가 같은 형식을 쓴다.
 *
 * <p><b>정기고사는 여기 없다</b> — 선생님만 보기로 확정된 데이터다.
 * 등수·백분위·반 평균도 없다.
 */
export interface StudentScoreData {
  /** 화면 맨 위 요약 박스용. 최신 주차부터 내림차순이다. */
  retestScheduled: { testType: WeeklyTestType; weekLabel: string; label: string }[];
  /** 데이터가 있는 종류만 들어 있다. 빈 섹션은 오지 않는다. */
  sections: StudentScoreSection[];
}

export interface StudentExamSchedule {
  examType: ExamType;
  startDate: string;
  endDate: string;
  scopeNote: string | null;
  /** 음수면 이미 지난 시험이다. */
  dDay: number;
}
