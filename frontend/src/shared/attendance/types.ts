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
 * 색상만으로 구분하지 않는다. 색약 사용자를 위해 칸 안에 짧은 기호를 함께 넣고
 * 하단에 범례를 둔다.
 */
export const DAY_STATUS_STYLE: Record<DayStatus, { label: string; mark: string; cell: string }> = {
  PRESENT: { label: "출석", mark: "출", cell: "bg-emerald-100 text-emerald-800" },
  LATE: { label: "지각", mark: "지", cell: "bg-amber-100 text-amber-800" },
  ABSENT: { label: "결석", mark: "결", cell: "bg-red-100 text-red-800" },
  SICK: { label: "병결", mark: "병", cell: "bg-sky-100 text-sky-800" },
  EXCUSED: { label: "공결", mark: "공", cell: "bg-slate-200 text-slate-700" },
  PENDING: { label: "미확인", mark: "—", cell: "bg-slate-100 text-slate-400" },
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
