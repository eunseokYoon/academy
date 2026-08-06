/**
 * 출석 관련 공용 타입과 표시 규칙. S-6(학생) · P-2(학부모) · T-5(선생님)가 같이 쓴다.
 *
 * PENDING은 출석이 아니라 "아직 확정되지 않은 날"이다. 미래 수업일도 PENDING이라
 * 이 구분이 없으면 아직 오지 않은 날이 초록색으로 보인다.
 */
export type AttendanceStatus = "PRESENT" | "LATE" | "ABSENT" | "SICK" | "EXCUSED";
export type DayStatus = AttendanceStatus | "PENDING";

export interface AttendanceSummary {
  present: number;
  late: number;
  absent: number;
  sick: number;
  excused: number;
}

export interface AttendanceDay {
  date: string;
  status: DayStatus;
  /** Phase 5 전까지 null. 숙제가 없던 날도 null이다 — 0(전부 미제출)과 다르다. */
  homeworkRate: number | null;
}

export interface AttendanceCalendar {
  year: number;
  month: number;
  summary: AttendanceSummary;
  homeworkCompletionRate: number | null;
  days: AttendanceDay[];
}

/**
 * 색상만으로 구분하지 않는다. 캘린더 칸에도 목록에도 label을 그대로 적는다 —
 * 색약 사용자에게 통하는 건 글씨뿐이다.
 *
 * <p>예전에는 "출·지·결" 같은 한 글자 mark를 칸에 넣었는데, 칸에 수업·클리닉이 같이
 * 들어오면서 "수 출"처럼 암호가 됐다. 지금은 전부 풀어 쓴다.
 *
 * <p>여기 색은 브랜드 남색으로 바꾸지 마라. 포인트 색이 빨강에서 파랑으로 옮겨간 덕분에
 * 빨강이 이 표에서 "결석" 하나만 뜻하게 됐다 — 브랜드색과 겹치지 않는 게 이득이다.
 */
export const DAY_STATUS_STYLE: Record<DayStatus, { label: string; cell: string }> = {
  PRESENT: { label: "출석", cell: "bg-emerald-100 text-emerald-800 ring-emerald-200" },
  LATE: { label: "지각", cell: "bg-amber-100 text-amber-800 ring-amber-200" },
  ABSENT: { label: "결석", cell: "bg-red-100 text-red-800 ring-red-200" },
  SICK: { label: "병결", cell: "bg-sky-100 text-sky-800 ring-sky-200" },
  EXCUSED: { label: "공결", cell: "bg-slate-200 text-slate-700 ring-slate-300" },
  PENDING: { label: "미확인", cell: "bg-slate-100 text-slate-400 ring-slate-200" },
};

/** T-5에서 선생님이 고를 수 있는 예외 상태. 기본값 PRESENT는 고를 필요가 없다. */
export const EXCEPTION_STATUSES: AttendanceStatus[] = ["LATE", "ABSENT", "SICK", "EXCUSED"];

export const STATUS_LABEL: Record<AttendanceStatus, string> = {
  PRESENT: "출석",
  LATE: "지각",
  ABSENT: "결석",
  SICK: "병결",
  EXCUSED: "공결",
};
