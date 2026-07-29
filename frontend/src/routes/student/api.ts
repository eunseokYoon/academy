import { del, get, post } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceCalendar } from "../../shared/attendance/types";
import type { SubmissionStatus } from "../../shared/homework/types";

export type ReservationStatus = "RESERVED" | "CANCELED" | "MOVED";
export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface MyReservation {
  reservationId: number;
  status: ReservationStatus;
  /** 대기 중인 변경 요청이 있을 때만 값이 있다. */
  changeRequestStatus: ChangeRequestStatus | null;
}

/** 다른 학생 이름은 내려오지 않는다. 인원 수만이다. */
export interface StudentClinic {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  capacity: number | null;
  reservedCount: number;
  /** 서버가 계산한다. capacity가 null이면 항상 false다. */
  full: boolean;
  myReservation: MyReservation | null;
}

// ---------- 출석 (S-6) ----------

export const getMyAttendances = (year: number, month: number) =>
  get<AttendanceCalendar>("/student/attendances", { year, month });

// ---------- 클리닉 (S-9) ----------

export const listMyClinics = (from: string, to: string) =>
  get<StudentClinic[]>("/student/clinics", { from, to });

export const reserveClinic = (clinicId: number) =>
  post<{ reservationId: number; clinicId: number; status: ReservationStatus }>(
    `/student/clinics/${clinicId}/reservation`,
  );

export const cancelClinicReservation = (clinicId: number) =>
  del<void>(`/student/clinics/${clinicId}/reservation`);

/** 학생이 직접 시간을 옮기지 못한다. 요청하면 선생님이 승인한다. */
export const requestClinicChange = (body: {
  reservationId: number;
  targetClinicId: number | null;
  reasonCode: string;
  reasonNote: string | null;
}) => post<{ requestId: number; status: ChangeRequestStatus }>(
  "/student/clinic-change-requests",
  body,
);

// ---------- 숙제 (S-2 · S-3 · S-4) ----------

export interface StudentHomeworkListItem {
  homeworkId: number;
  title: string;
  classRoomName: string;
  dueAt: string;
  status: SubmissionStatus;
  isLate: boolean;
  photoCount: number;
  hasVideo: boolean;
  hasFeedback: boolean;
  /** 서버가 계산한다. 음수면 마감이 지난 것이다. */
  remainingMinutes: number;
}

export interface StudentHomeworkDetail {
  homework: {
    id: number;
    title: string;
    description: string | null;
    dueAt: string;
    classRoomName: string;
  };
  submission: {
    id: number;
    status: SubmissionStatus;
    submittedAt: string | null;
    isLate: boolean;
    photos: { photoId: number; url: string; sortOrder: number }[];
    /** 최대 1개. 없으면 null이다. */
    video: { url: string; bytes: number | null } | null;
  };
  /** 선생님 피드백. 학생 화면에만 나온다. */
  feedback: { content: string; createdAt: string } | null;
}

export const listMyHomeworks = (params: { status?: SubmissionStatus; page?: number }) =>
  get<PageResponse<StudentHomeworkListItem>>("/student/homeworks", params);

export const getMyHomework = (homeworkId: number) =>
  get<StudentHomeworkDetail>(`/student/homeworks/${homeworkId}`);

export const issueUploadUrl = (
  homeworkId: number,
  body: { contentType: string; bytes: number },
) => post<{ uploadUrl: string; s3Key: string }>(
  `/student/homeworks/${homeworkId}/photos/upload-url`,
  body,
);

export const registerPhoto = (
  homeworkId: number,
  body: { s3Key: string; sortOrder: number; bytes: number },
) => post<{ photoId: number; photoCount: number }>(
  `/student/homeworks/${homeworkId}/photos`,
  body,
);

export const deletePhoto = (homeworkId: number, photoId: number) =>
  del<void>(`/student/homeworks/${homeworkId}/photos/${photoId}`);

export const issueVideoUploadUrl = (
  homeworkId: number,
  body: { contentType: string; bytes: number },
) => post<{ uploadUrl: string; s3Key: string }>(
  `/student/homeworks/${homeworkId}/video/upload-url`,
  body,
);

/** 이미 영상이 있으면 덮어쓴다. 서버가 이전 파일을 S3에서 지운다. */
export const registerVideo = (homeworkId: number, body: { s3Key: string; bytes: number }) =>
  post<{ url: string; bytes: number | null }>(`/student/homeworks/${homeworkId}/video`, body);

export const deleteVideo = (homeworkId: number) =>
  del<void>(`/student/homeworks/${homeworkId}/video`);

export const submitHomework = (homeworkId: number) =>
  post<{
    submissionId: number;
    status: SubmissionStatus;
    submittedAt: string;
    isLate: boolean;
    photoCount: number;
  }>(`/student/homeworks/${homeworkId}/submit`);
