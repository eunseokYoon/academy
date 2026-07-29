import { get } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceCalendar, AttendanceStatus } from "../../shared/attendance/types";
import type { SubmissionStatus } from "../../shared/homework/types";

export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

/** 학부모는 조회만 한다. 신청·취소·변경 경로는 없다. */
export interface ParentClinic {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  /** null이면 아직 출석 확정 전이다. */
  attendStatus: AttendanceStatus | null;
  changeRequestStatus: ChangeRequestStatus | null;
}

export const getChildAttendances = (studentId: number, year: number, month: number) =>
  get<AttendanceCalendar>(`/parent/children/${studentId}/attendances`, { year, month });

export const getChildClinics = (studentId: number, from: string, to: string) =>
  get<ParentClinic[]>(`/parent/children/${studentId}/clinics`, { from, to });

// ---------- 숙제 (P-3) ----------

/**
 * 학부모는 <b>했는지 여부만</b> 본다.
 * 숙제 내용·사진·피드백은 응답에 없다. 서버 DTO가 따로다.
 */
export interface ParentHomework {
  homeworkId: number;
  title: string;
  classRoomName: string;
  dueAt: string;
  status: SubmissionStatus;
  isLate: boolean;
  checked: boolean;
}

export const getChildHomeworks = (
  studentId: number,
  params: { status?: SubmissionStatus; page?: number },
) => get<PageResponse<ParentHomework>>(`/parent/children/${studentId}/homeworks`, params);
