import { del, get, patch, post } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceStatus, AttendanceSummary } from "../../shared/attendance/types";
import type { HomeworkCounts, SubmissionStatus } from "../../shared/homework/types";

export type StudentStatus = "ENROLLED" | "WITHDRAWN";
export type ClassRoomStatus = "ACTIVE" | "CLOSED";
export type CodeTarget = "STUDENT" | "PARENT";
/** 수업의 출석 확정 여부. 학생 개인의 출석 상태(AttendanceStatus)와 다른 축이다. */
export type LessonAttendanceStatus = "PENDING" | "CONFIRMED";

export interface SignupCode {
  target: CodeTarget;
  code: string;
  phone: string;
  expiresAt: string;
}

export interface StudentListItem {
  studentId: number;
  name: string;
  classRooms: string[];
  studentPhone: string | null;
  studentSignedUp: boolean;
  parentPhone: string | null;
  parentLinked: boolean;
  status: StudentStatus;
  createdAt: string;
}

export interface StudentDetail {
  studentId: number;
  name: string;
  memo: string | null;
  status: StudentStatus;
  withdrawnAt: string | null;
  createdAt: string;
  studentPhone: string | null;
  studentSignedUp: boolean;
  parentPhone: string | null;
  parentLinked: boolean;
  parentName: string | null;
  classRooms: { classRoomId: number; name: string; joinedAt: string }[];
  signupCodes: SignupCode[];
}

export interface StudentCreated {
  studentId: number;
  signupCodes: {
    student: { code: string; phone: string; expiresAt: string };
    parent: { code: string; phone: string; expiresAt: string };
  };
}

export interface ClassRoom {
  classRoomId: number;
  name: string;
  dayOfWeek: number | null;
  startTime: string | null;
  termStart: string | null;
  termEnd: string | null;
  status: ClassRoomStatus;
  joinCode: string;
  joinCodeActive: boolean;
  memo: string | null;
  studentCount: number;
}

export interface ClassRoomStudents {
  classRoom: { id: number; name: string };
  students: { studentId: number; name: string; joinedAt: string; signedUp: boolean }[];
}

export interface LessonListItem {
  lessonId: number;
  classRoomId: number;
  classRoomName: string;
  lessonDate: string;
  year: number;
  month: number;
  week: number;
  title: string | null;
  contentWritten: boolean;
  published: boolean;
  attendanceStatus: LessonAttendanceStatus;
}

export interface LessonDetail extends Omit<LessonListItem, "contentWritten" | "published"> {
  videoUrl: string | null;
  videoId: string | null;
  embedUrl: string | null;
  content: string | null;
  keyPoints: string | null;
  nextPreview: string | null;
  publishedAt: string | null;
}

export interface StudentListParams {
  classRoomId?: number;
  status?: StudentStatus;
  keyword?: string;
  sort?: "name" | "recent";
  page?: number;
  size?: number;
}

// ---------- 학생 (T-2) ----------

export const listStudents = (params: StudentListParams) =>
  get<PageResponse<StudentListItem>>("/teacher/students", params);

export const getStudent = (studentId: number) =>
  get<StudentDetail>(`/teacher/students/${studentId}`);

export const createStudent = (body: {
  name: string;
  studentPhone: string;
  parentPhone: string;
  memo: string | null;
  classRoomIds: number[];
}) => post<StudentCreated>("/teacher/students", body);

export const updateStudent = (
  studentId: number,
  body: Partial<{ name: string; memo: string; studentPhone: string; parentPhone: string }>,
) => patch<StudentDetail>(`/teacher/students/${studentId}`, body);

export const withdrawStudent = (studentId: number, withdrawnAt: string) =>
  post<StudentDetail>(`/teacher/students/${studentId}/withdraw`, { withdrawnAt });

export const restoreStudent = (studentId: number) =>
  post<{ studentId: number; status: StudentStatus; restoredEnrollments: number }>(
    `/teacher/students/${studentId}/restore`,
  );

export const deleteStudent = (studentId: number) =>
  del<{ deletedStudentId: number; deletedUser: boolean; deletedEnrollments: number }>(
    `/teacher/students/${studentId}`,
  );

export const issueSignupCode = (studentId: number, body: { target: CodeTarget; phone?: string }) =>
  post<SignupCode>(`/teacher/students/${studentId}/signup-code`, body);

export const resetPassword = (
  studentId: number,
  body: { target: CodeTarget; newPassword: string | null },
) =>
  post<{ target: CodeTarget; loginId: string; temporaryPassword: string }>(
    `/teacher/students/${studentId}/reset-password`,
    body,
  );

// ---------- 반 (T-3) ----------

export const listClassRooms = (status?: ClassRoomStatus) =>
  get<ClassRoom[]>("/teacher/class-rooms", status ? { status } : undefined);

export const getClassRoom = (classRoomId: number) =>
  get<ClassRoom>(`/teacher/class-rooms/${classRoomId}`);

export interface ClassRoomForm {
  name: string;
  dayOfWeek: number | null;
  startTime: string | null;
  termStart: string | null;
  termEnd: string | null;
  memo: string | null;
}

export const createClassRoom = (body: ClassRoomForm) =>
  post<{ classRoomId: number; name: string; joinCode: string; joinCodeActive: boolean }>(
    "/teacher/class-rooms",
    body,
  );

export const updateClassRoom = (classRoomId: number, body: Partial<ClassRoomForm>) =>
  patch<ClassRoom>(`/teacher/class-rooms/${classRoomId}`, body);

export const deleteClassRoom = (classRoomId: number) =>
  del<void>(`/teacher/class-rooms/${classRoomId}`);

export const closeClassRoom = (classRoomId: number) =>
  post<ClassRoom>(`/teacher/class-rooms/${classRoomId}/close`);

export const changeJoinCode = (
  classRoomId: number,
  body: { regenerate?: boolean; active?: boolean },
) => post<{ joinCode: string; joinCodeActive: boolean }>(
  `/teacher/class-rooms/${classRoomId}/join-code`,
  body,
);

export const listClassRoomStudents = (classRoomId: number, asOf?: string) =>
  get<ClassRoomStudents>(`/teacher/class-rooms/${classRoomId}/students`, asOf ? { asOf } : undefined);

export const assignStudents = (classRoomId: number, studentIds: number[], joinedAt?: string) =>
  post<ClassRoomStudents>(`/teacher/class-rooms/${classRoomId}/students`, {
    studentIds,
    joinedAt: joinedAt ?? null,
  });

export const unassignStudent = (classRoomId: number, studentId: number) =>
  del<void>(`/teacher/class-rooms/${classRoomId}/students/${studentId}`);

// ---------- 수업 (T-4) ----------

export interface LessonListParams {
  classRoomId?: number;
  from?: string;
  to?: string;
  year?: number;
  month?: number;
  week?: number;
}

export const listLessons = (params: LessonListParams) =>
  get<LessonListItem[]>("/teacher/lessons", params);

export const getLesson = (lessonId: number) => get<LessonDetail>(`/teacher/lessons/${lessonId}`);

export const createLesson = (body: {
  classRoomId: number;
  lessonDate: string;
  year: number;
  month: number;
  week: number;
  title?: string | null;
}) => post<LessonDetail>("/teacher/lessons", body);

export const bulkCreateLessons = (body: {
  classRoomId: number;
  from: string;
  to: string;
  skipDates: string[];
}) => post<{ created: number; skipped: number; createdDates: string[] }>(
  "/teacher/lessons/bulk",
  body,
);

export const updateLesson = (
  lessonId: number,
  body: Partial<{
    year: number;
    month: number;
    week: number;
    title: string;
    videoUrl: string;
    content: string;
    keyPoints: string;
    nextPreview: string;
  }>,
) => patch<LessonDetail>(`/teacher/lessons/${lessonId}`, body);

export const publishLesson = (lessonId: number) =>
  post<LessonDetail>(`/teacher/lessons/${lessonId}/publish`);

export const deleteLesson = (lessonId: number) => del<void>(`/teacher/lessons/${lessonId}`);

// ---------- 출석 (T-5) ----------

export interface AttendanceRosterRow {
  studentId: number;
  name: string;
  status: AttendanceStatus;
  memo: string | null;
}

export interface AttendanceRoster {
  lessonId: number;
  classRoomId: number;
  classRoomName: string;
  lessonDate: string;
  attendanceStatus: LessonAttendanceStatus;
  students: AttendanceRosterRow[];
}

export interface PendingLesson {
  lessonId: number;
  classRoomId: number;
  classRoomName: string;
  lessonDate: string;
  studentCount: number;
}

/** 안 온 학생만 담는다. 전원 출석이면 빈 배열이다. */
export interface AttendanceException {
  studentId: number;
  status: AttendanceStatus;
  memo: string | null;
}

export const getAttendanceRoster = (lessonId: number) =>
  get<AttendanceRoster>(`/teacher/lessons/${lessonId}/attendance`);

export const confirmAttendance = (lessonId: number, exceptions: AttendanceException[]) =>
  post<{
    lessonId: number;
    attendanceStatus: LessonAttendanceStatus;
    confirmedAt: string;
    summary: AttendanceSummary;
  }>(`/teacher/lessons/${lessonId}/attendance/confirm`, { exceptions });

export const listPendingAttendance = () =>
  get<PendingLesson[]>("/teacher/attendance/pending");

// ---------- 클리닉 (T-13) ----------

export type ClinicStatus = "OPEN" | "CLOSED";
export type ReservationStatus = "RESERVED" | "CANCELED" | "MOVED";
export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface Clinic {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  capacity: number | null;
  reservedCount: number;
  status: ClinicStatus;
  attendanceConfirmed: boolean;
  memo: string | null;
}

export interface ClinicReservationRow {
  reservationId: number;
  studentId: number;
  name: string;
  /** 학생 본인 신청인지 선생님 배정인지. "왜 여기 있냐"는 문의에 답하려면 필요하다. */
  assignedByTeacher: boolean;
  attendStatus: AttendanceStatus | null;
  memo: string | null;
}

export interface ClinicReservations {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  capacity: number | null;
  attendanceConfirmed: boolean;
  students: ClinicReservationRow[];
}

export interface ClinicSlot {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
}

export interface ClinicChangeRequest {
  requestId: number;
  studentId: number;
  studentName: string;
  from: ClinicSlot;
  /** null이면 취소 요청이다. */
  to: ClinicSlot | null;
  reasonCode: string;
  reasonNote: string | null;
  status: ChangeRequestStatus;
  requestedAt: string;
  decidedAt: string | null;
}

export const listClinics = (from: string, to: string, status?: ClinicStatus) =>
  get<Clinic[]>("/teacher/clinics", { from, to, status });

export const createClinic = (body: {
  clinicDate: string;
  startTime: string;
  endTime: string;
  capacity: number | null;
  memo: string | null;
}) => post<{ clinicId: number; reservedCount: number; capacity: number | null; status: ClinicStatus }>(
  "/teacher/clinics",
  body,
);

export const updateClinic = (
  clinicId: number,
  body: Partial<{
    clinicDate: string;
    startTime: string;
    endTime: string;
    capacity: number;
    clearCapacity: boolean;
    memo: string;
    status: ClinicStatus;
  }>,
) => patch<Clinic>(`/teacher/clinics/${clinicId}`, body);

export const deleteClinic = (clinicId: number) => del<void>(`/teacher/clinics/${clinicId}`);

export const listClinicReservations = (clinicId: number) =>
  get<ClinicReservations>(`/teacher/clinics/${clinicId}/reservations`);

export const assignClinicStudents = (clinicId: number, studentIds: number[]) =>
  post<ClinicReservations>(`/teacher/clinics/${clinicId}/students`, { studentIds });

export const unassignClinicStudent = (clinicId: number, studentId: number) =>
  del<void>(`/teacher/clinics/${clinicId}/students/${studentId}`);

export const confirmClinicAttendance = (clinicId: number, exceptions: AttendanceException[]) =>
  post<{ clinicId: number; confirmedAt: string; summary: AttendanceSummary }>(
    `/teacher/clinics/${clinicId}/attendance/confirm`,
    { exceptions },
  );

export const listClinicChangeRequests = (status: ChangeRequestStatus = "PENDING") =>
  get<ClinicChangeRequest[]>("/teacher/clinic-change-requests", { status });

export const decideClinicChangeRequest = (requestId: number, approve: boolean) =>
  post<ClinicChangeRequest>(`/teacher/clinic-change-requests/${requestId}/decide`, {
    approve,
    note: null,
  });

// ---------- 숙제 (T-6 · T-7) ----------

export interface HomeworkTemplate {
  id: number;
  title: string;
  description: string | null;
  useCount: number;
}

export interface HomeworkListItem {
  homeworkId: number;
  classRoomId: number;
  classRoomName: string;
  /** null이면 캘린더 숙제 완료율 계산에서 빠진다. 목록에서 눈에 띄게 표시한다. */
  lessonId: number | null;
  title: string;
  dueAt: string;
  counts: HomeworkCounts;
}

export interface HomeworkDetail extends Omit<HomeworkListItem, "counts"> {
  lessonDate: string | null;
  description: string | null;
  counts: HomeworkCounts;
}

export interface SubmissionListItem {
  submissionId: number;
  studentId: number;
  studentName: string;
  status: SubmissionStatus;
  submittedAt: string | null;
  isLate: boolean;
  photoCount: number;
  hasFeedback: boolean;
  /** 첫 사진의 조회용 presigned URL. 미제출이면 null이다. */
  thumbnailUrl: string | null;
  /** 영상은 썸네일을 만들 수 없다(트랜스코딩 없음). 격자에서는 표시만 한다. */
  hasVideo: boolean;
}

export interface HomeworkSubmissions {
  homework: { id: number; title: string; classRoomName: string; dueAt: string };
  counts: HomeworkCounts;
  items: SubmissionListItem[];
}

export interface SubmissionPhoto {
  photoId: number;
  url: string;
  sortOrder: number;
}

export interface SubmissionDetail {
  submissionId: number;
  studentId: number;
  studentName: string;
  status: SubmissionStatus;
  submittedAt: string | null;
  isLate: boolean;
  photos: SubmissionPhoto[];
  /** 최대 1개. 없으면 null이다. */
  video: { url: string; bytes: number | null } | null;
  feedback: { content: string; createdAt: string } | null;
  prevSubmissionId: number | null;
  /** 아직 확인하지 않은 다음 제출물. "저장하고 다음"이 이 값을 쓴다. */
  nextSubmissionId: number | null;
}

export interface PendingHomework {
  homeworkId: number;
  title: string;
  classRoomName: string;
  dueAt: string;
  notSubmitted: number;
  awaitingCheck: number;
}

export const listHomeworkTemplates = () =>
  get<HomeworkTemplate[]>("/teacher/homework-templates");

export const createHomeworkTemplate = (body: { title: string; description: string | null }) =>
  post<HomeworkTemplate>("/teacher/homework-templates", body);

export const deleteHomeworkTemplate = (templateId: number) =>
  del<void>(`/teacher/homework-templates/${templateId}`);

export const listHomeworks = (params: {
  classRoomId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}) => get<PageResponse<HomeworkListItem>>("/teacher/homeworks", params);

export const getHomework = (homeworkId: number) =>
  get<HomeworkDetail>(`/teacher/homeworks/${homeworkId}`);

export const createHomework = (body: {
  classRoomId: number;
  lessonId: number | null;
  title: string;
  description: string | null;
  dueAt: string;
  templateId: number | null;
  saveAsTemplate: boolean;
}) => post<{ homeworkId: number; targetCount: number }>("/teacher/homeworks", body);

/** 마감은 늦추는 방향만 허용된다. 앞당기려면 삭제 후 재출제한다. */
export const updateHomework = (
  homeworkId: number,
  body: { title: string; description: string | null; lessonId: number | null; dueAt?: string },
) => patch<HomeworkDetail>(`/teacher/homeworks/${homeworkId}`, body);

export const deleteHomework = (homeworkId: number) =>
  del<void>(`/teacher/homeworks/${homeworkId}`);

export const listPendingHomeworks = () =>
  get<PendingHomework[]>("/teacher/homeworks/pending");

export const listSubmissions = (homeworkId: number) =>
  get<HomeworkSubmissions>(`/teacher/homeworks/${homeworkId}/submissions`);

export const getSubmission = (submissionId: number) =>
  get<SubmissionDetail>(`/teacher/submissions/${submissionId}`);

export const createFeedback = (submissionId: number, content: string) =>
  post<{ content: string; createdAt: string }>(
    `/teacher/submissions/${submissionId}/feedback`,
    { content },
  );

export const updateFeedback = (submissionId: number, content: string) =>
  patch<{ content: string; createdAt: string }>(
    `/teacher/submissions/${submissionId}/feedback`,
    { content },
  );

/** 피드백 없이 확인만. 200명 전원에게 글을 쓰는 건 불가능하다. */
export const checkSubmission = (submissionId: number) =>
  post<void>(`/teacher/submissions/${submissionId}/check`);
