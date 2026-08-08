import { del, get, patch, post, put } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceStatus, AttendanceSummary } from "../../shared/attendance/types";
import type {
  HomeworkCounts,
  HomeworkKind,
  HomeworkResult,
  SubmissionStatus,
} from "../../shared/homework/types";
import type { MaterialCategory, MaterialVisibility } from "../../shared/material/types";
import type { NoticeScope } from "../../shared/notice/api";
import type { LessonChangeRequest } from "../../shared/lessonchange/types";
import type { OnlineTestTakeStatus } from "../../shared/onlinetest/types";
import type {
  ExamType,
  RegularExamGrid,
  RegularExamSaveBody,
  WeeklyTestGrid,
  WeeklyTestSaveBody,
} from "../../shared/score/types";

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

/**
 * 코드는 학생 한 장뿐이다. 학부모 계정은 등록 시점에 보호자 번호로 바로 만들어지고
 * 초기 비밀번호는 0000이다 — 전달할 코드가 없다.
 */
export interface StudentCreated {
  studentId: number;
  signupCode: { code: string; phone: string; expiresAt: string };
}

/**
 * 반의 주간 수업 슬롯. 요일당 하나다.
 *
 * dayOfWeek: 1=월 ~ 7=일 (ISO-8601). 시각은 "19:00" 형식이다.
 * endTime은 없을 수 있다 — 기존 반 이관분에는 종료시각이 없다.
 */
export interface ClassRoomSchedule {
  dayOfWeek: number;
  startTime: string;
  endTime: string | null;
}

export interface ClassRoom {
  classRoomId: number;
  name: string;
  /** 빈 배열이면 요일 미정이다. 그 반에는 수업일 일괄 생성을 쓸 수 없다. */
  schedules: ClassRoomSchedule[];
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
  /**
   * 수정(PATCH)에서 이 필드를 <b>빼면</b> 스케줄을 건드리지 않고,
   * 빈 배열을 보내면 전부 지운다(요일 미정). 서버가 통째로 교체한다.
   */
  schedules: ClassRoomSchedule[];
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

/**
 * 미확정 클리닉. 반 이름이 없다 — 클리닉은 반과 무관하게 열리는 보충 시간대라
 * 시각이 그 시간대의 이름 역할을 한다.
 */
export interface PendingClinic {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  /** 신청 인원. 수업의 재원 인원과 뜻이 다르다. */
  studentCount: number;
}

/** 확정 API가 서로 달라서 한 배열로 합쳐 오지 않는다. */
export interface PendingAttendance {
  lessons: PendingLesson[];
  clinics: PendingClinic[];
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
  get<PendingAttendance>("/teacher/attendance/pending");

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

// ---------- 수업일 변경 (T-13) ----------

export const listLessonChangeRequests = (status: ChangeRequestStatus = "PENDING") =>
  get<LessonChangeRequest[]>("/teacher/lesson-change-requests", { status });

/**
 * 승인하면 그 순간 학생·학부모에게 개인 공지가 발행된다.
 * <b>반 배정도 수업도 출석도 바뀌지 않는다</b> — 원래 반 출석은 직접 처리해야 한다.
 */
export const decideLessonChangeRequest = (requestId: number, approve: boolean) =>
  post<LessonChangeRequest>(`/teacher/lesson-change-requests/${requestId}/decide`, { approve });

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
  kind: HomeworkKind;
  /** GRID 열은 재제출을 열기 전까지 마감이 없다. */
  dueAt: string | null;
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
  /** GRID 열은 재제출을 열기 전까지 마감이 없다. */
  homework: {
    id: number;
    title: string;
    classRoomName: string;
    kind: HomeworkKind;
    dueAt: string | null;
  };
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

// ---------- T-6b 숙제 그리드 ----------

/**
 * 칸 하나. result가 null이면 회색 "미채점"이다.
 *
 * <p>status는 온라인 제출 축이라 ⭕를 받은 학생은 계속 NOT_SUBMITTED다 — 정상이다.
 * 채점 결과를 status로 판단하지 마라.
 */
export interface GridCell {
  studentId: number;
  result: HomeworkResult | null;
  completionRate: number | null;
  resolvedByResubmission: boolean;
  status: SubmissionStatus;
  photoCount: number;
  hasVideo: boolean;
}

export interface GridColumn {
  homeworkId: number;
  title: string;
  sortOrder: number;
  /** null이면 재제출을 아직 안 연 것이다. 그 상태에선 아무도 온라인으로 못 낸다. */
  resubmitDueAt: string | null;
  resubmitTargetCount: number;
  awaitingCheckCount: number;
  cells: GridCell[];
}

export interface HomeworkGrid {
  lesson: {
    id: number;
    lessonDate: string;
    classRoomId: number;
    classRoomName: string;
  };
  /** 그 수업일 기준 재원생. 칸이 아직 없는 학생도 빈 칸으로 들어 있다. */
  students: { studentId: number; name: string }[];
  columns: GridColumn[];
}

export interface HomeworkGridSaveBody {
  lessonId: number;
  columns: {
    /** null이면 새 열. 저장하면서 대상 전원의 칸이 미채점으로 깔린다. */
    homeworkId: number | null;
    title: string;
    sortOrder: number;
    cells: {
      studentId: number;
      /** null이면 "미채점으로 되돌린다". 행은 지워지지 않는다. */
      result: HomeworkResult | null;
      /** PARTIAL에만 붙고 1~99다. 0·100은 ❌·⭕가 이미 표현한다. */
      completionRate: number | null;
    }[];
  }[];
}

export const getHomeworkGrid = (lessonId: number) =>
  get<HomeworkGrid>("/teacher/homework-grid", { lessonId });

/**
 * 그리드 한 장 통째로 저장.
 *
 * <p>배열에서 열을 빼도 지워지지 않는다. 삭제는 deleteHomework뿐이다 —
 * 통신이 끊긴 저장 한 번에 학생 제출물이 날아가면 안 된다.
 */
export const saveHomeworkGrid = (body: HomeworkGridSaveBody) =>
  put<HomeworkGrid>("/teacher/homework-grid", body);

/** 재제출 열기. dueAt을 생략하면 서버가 그 반의 다음 수업일 21:00으로 잡는다. */
export const openResubmit = (homeworkId: number, dueAt: string | null) =>
  post<{ targetCount: number; dueAt: string }>(
    `/teacher/homeworks/${homeworkId}/resubmit-request`,
    dueAt === null ? {} : { dueAt },
  );

/** 잘못 연 열을 되돌린다. 이미 낸 학생이 있으면 409다. */
export const closeResubmit = (homeworkId: number) =>
  del<void>(`/teacher/homeworks/${homeworkId}/resubmit-request`);

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

// ---------- 시청 현황 (Phase 6) ----------

/** 미시청 학생도 포함된다. 선생님이 보려는 건 안 본 학생이다. */
export interface LessonViews {
  lessonId: number;
  totalStudents: number;
  viewedCount: number;
  items: {
    studentId: number;
    name: string;
    viewed: boolean;
    firstViewedAt: string | null;
    watchSeconds: number;
  }[];
}

export const getLessonViews = (lessonId: number) =>
  get<LessonViews>(`/teacher/lessons/${lessonId}/views`);

// ---------- 시험 일정 (T-11) ----------

export interface ExamSchedule {
  examScheduleId: number;
  classRoomId: number;
  classRoomName: string;
  year: number;
  semester: number;
  examType: ExamType;
  startDate: string;
  endDate: string;
  scopeNote: string | null;
}

export const listExamSchedules = (params: { classRoomId?: number; year?: number }) =>
  get<ExamSchedule[]>("/teacher/exam-schedules", params);

/**
 * 반마다 한 행이다. 여러 반에 같은 시험을 등록할 때는 이 함수를 반 수만큼 호출한다.
 * 학교·학년 개념이 없어 한 번에 묶을 수 없다.
 */
export const createExamSchedule = (body: {
  classRoomId: number;
  year: number;
  semester: number;
  examType: ExamType;
  startDate: string;
  endDate: string;
  scopeNote: string | null;
}) => post<ExamSchedule>("/teacher/exam-schedules", body);

export const updateExamSchedule = (
  examScheduleId: number,
  body: { startDate?: string; endDate?: string; scopeNote?: string | null },
) => patch<ExamSchedule>(`/teacher/exam-schedules/${examScheduleId}`, body);

export const deleteExamSchedule = (examScheduleId: number) =>
  del<void>(`/teacher/exam-schedules/${examScheduleId}`);

// ---------- 성적 기입 · 정기고사 (T-8) ----------

/** 그리드 한 장. tests는 항상 4종이 순서대로 들어 있다. */
export const getWeeklyTestGrid = (params: {
  classRoomId: number;
  year: number;
  month: number;
  week: number;
}) => get<WeeklyTestGrid>("/teacher/weekly-tests", params);

/**
 * 그리드 한 장 통째로 저장. 요청에 들어온 종류·학생만 반영된다.
 *
 * <p>셀 값이 전부 null이면 그 행이 삭제되고, 헤더값을 비우면 그 종류가 통째로 사라진다.
 * 같은 칸을 다시 저장하는 건 오타 수정이라는 정상 흐름이라 409가 아니라 덮어쓰기다.
 */
export const saveWeeklyTests = (body: WeeklyTestSaveBody) =>
  put<void>("/teacher/weekly-tests", body);

/** 정기고사. <b>선생님 전용</b>이라 학생·학부모 화면에서 호출하지 마라. */
export const getRegularExamGrid = (params: { classRoomId: number; year: number }) =>
  get<RegularExamGrid>("/teacher/regular-exams", params);

export const saveRegularExams = (body: RegularExamSaveBody) =>
  put<void>("/teacher/regular-exams", body);

// ---------- 온라인 테스트 (T-14) ----------

export interface OnlineTestListItem {
  testId: number;
  title: string;
  classRoomId: number;
  classRoomName: string;
  questionCount: number;
  year: number;
  month: number;
  week: number;
  /** 앞 N문항이 내부지문. null이면 내부·외부 집계를 하지 않는다. */
  internalQuestionCount: number | null;
  published: boolean;
  opensAt: string | null;
  closesAt: string | null;
}

/** 선생님 전용이다. 정답이 들어 있으니 학생 화면에서 쓰지 마라. */
export interface OnlineTestDetail extends Omit<OnlineTestListItem, "published"> {
  choiceCount: number;
  correctChoices: number[];
  points: number[] | null;
  answerS3Key: string | null;
  answerFileUrl: string | null;
  publishedAt: string | null;
}

export interface OnlineTestResults {
  test: {
    testId: number;
    title: string;
    questionCount: number;
    internalQuestionCount: number | null;
    classRoomName: string;
  };
  counts: { total: number; notStarted: number; inProgress: number; submitted: number };
  /** 제출자만으로 계산한다. 제출이 없으면 null. 선생님 화면에만 있는 값이다. */
  average: number | null;
  items: {
    studentId: number;
    name: string;
    status: OnlineTestTakeStatus;
    score: number | null;
    correctCount: number | null;
    /** test.internalQuestionCount가 null이거나 미제출이면 둘 다 null이다. */
    internalCorrect: number | null;
    externalCorrect: number | null;
    /** 1부터 센 문항 번호. 미제출이면 빈 배열이다. */
    wrongQuestionNos: number[];
    submittedAt: string | null;
  }[];
}

/** 학생 한 명의 문항별 정오. 선생님이 이걸 보고 성적 기입 탭에 직접 적는다. */
export interface OnlineTestStudentDetail {
  studentId: number;
  name: string;
  correctCount: number | null;
  questionCount: number;
  internalQuestionCount: number | null;
  results: {
    questionNo: number;
    /** null이면 미체크다. 오답 처리하고 감점은 없다. */
    chosen: number | null;
    correct: number;
    isCorrect: boolean;
    section: "INTERNAL" | "EXTERNAL" | null;
  }[];
}

export const listOnlineTests = (params: {
  classRoomId?: number;
  year?: number;
  month?: number;
  week?: number;
}) => get<OnlineTestListItem[]>("/teacher/online-tests", params);

export const getOnlineTest = (testId: number) =>
  get<OnlineTestDetail>(`/teacher/online-tests/${testId}`);

export const createOnlineTest = (body: {
  classRoomId: number;
  title: string;
  questionCount: number;
  choiceCount: number;
  correctChoices: number[];
  points: number[] | null;
  answerS3Key: string | null;
  /** 앞 N문항이 내부지문. null이면 내부·외부 집계를 하지 않는다. */
  internalQuestionCount: number | null;
  year: number;
  month: number;
  week: number;
  opensAt: string | null;
  closesAt: string | null;
}) => post<{ testId: number; targetCount: number; published: boolean }>(
  "/teacher/online-tests",
  body,
);

/** 공개 후에는 정답·문항 수를 보내면 409다. 제목·해설지·기간만 고칠 수 있다. */
export const updateOnlineTest = (
  testId: number,
  body: Partial<{
    title: string;
    questionCount: number;
    choiceCount: number;
    correctChoices: number[];
    points: number[] | null;
    answerS3Key: string | null;
    internalQuestionCount: number | null;
    year: number;
    month: number;
    week: number;
    opensAt: string | null;
    closesAt: string | null;
  }>,
) => patch<OnlineTestDetail>(`/teacher/online-tests/${testId}`, body);

export const issueAnswerUploadUrl = (body: { contentType: string; bytes: number }) =>
  post<{ uploadUrl: string; s3Key: string }>("/teacher/online-tests/upload-url", body);

export const publishOnlineTest = (testId: number) =>
  post<OnlineTestDetail>(`/teacher/online-tests/${testId}/publish`);

export const deleteOnlineTest = (testId: number) =>
  del<void>(`/teacher/online-tests/${testId}`);

export const getOnlineTestResults = (testId: number) =>
  get<OnlineTestResults>(`/teacher/online-tests/${testId}/results`);

export const getOnlineTestStudentDetail = (testId: number, studentId: number) =>
  get<OnlineTestStudentDetail>(`/teacher/online-tests/${testId}/results/${studentId}`);

// ---------- 대시보드 (T-1) ----------

/**
 * todo가 이 화면의 존재 이유다.
 *
 * <p>앞의 여섯 개는 할 일이고 <b>뒤의 두 개는 점검 항목</b>이다.
 * recentSignupCount·openJoinCodeCount는 <b>0이어도 숨기지 마라</b> —
 * 반 코드에는 전화번호 대조가 없어서 명단을 보는 것 자체가 방어 수단이다.
 */
export interface TeacherDashboard {
  today: {
    date: string;
    lessons: {
      lessonId: number;
      classRoomName: string;
      /** 반 일정이 비어 있으면 null이다. */
      startTime: string | null;
      studentCount: number;
      attendanceStatus: LessonAttendanceStatus;
      contentWritten: boolean;
    }[];
  };
  todo: {
    pendingAttendanceCount: number;
    awaitingCheckCount: number;
    unwrittenLessonCount: number;
    unsignedStudentCount: number;
    unlinkedParentCount: number;
    pendingClinicRequestCount: number;
    recentSignupCount: number;
    openJoinCodeCount: number;
  };
  stats: { totalStudents: number; activeClassRooms: number };
}

export const getDashboard = () => get<TeacherDashboard>("/teacher/dashboard");

// ---------- 자료실 (T-9) ----------

export interface Material {
  materialId: number;
  title: string;
  category: MaterialCategory;
  fileName: string;
  bytes: number | null;
  visibility: MaterialVisibility;
  /** PUBLIC이면 null이다. */
  classRoomId: number | null;
  classRoomName: string | null;
  year: number;
  month: number;
  week: number;
  createdAt: string;
}

export const listMaterials = (params: {
  year?: number;
  month?: number;
  week?: number;
  category?: MaterialCategory;
  page?: number;
}) => get<PageResponse<Material>>("/teacher/materials", params);

/**
 * 확장자 허용 목록과 50MB 상한을 서버가 여기서 검사한다.
 * 응답의 contentType으로 PUT해야 서명이 맞는다.
 */
export const issueMaterialUploadUrl = (body: { fileName: string; bytes: number }) =>
  post<{ uploadUrl: string; s3Key: string; contentType: string }>(
    "/teacher/materials/upload-url",
    body,
  );

/**
 * 같은 파일을 여러 반에 주려면 <b>s3Key를 공유해 반마다 호출</b>한다.
 * S3에는 한 번만 올린다.
 */
export const createMaterial = (body: {
  title: string;
  category: MaterialCategory;
  s3Key: string;
  fileName: string;
  bytes: number | null;
  classRoomId: number | null;
  visibility: MaterialVisibility;
  year: number;
  month: number;
  week: number;
}) => post<Material>("/teacher/materials", body);

/** 공개 범위와 파일은 못 바꾼다. 대상을 바꾸려면 지우고 다시 올린다. */
export const updateMaterial = (
  materialId: number,
  body: Partial<{
    title: string;
    category: MaterialCategory;
    year: number;
    month: number;
    week: number;
  }>,
) => patch<Material>(`/teacher/materials/${materialId}`, body);

export const deleteMaterial = (materialId: number) =>
  del<void>(`/teacher/materials/${materialId}`);

// ---------- 공지 (T-10) ----------

/** publishedAt이 null이면 초안이다 — 학생·학부모에게 안 나간 글이다. */
export interface TeacherNotice {
  noticeId: number;
  title: string;
  content: string;
  scope: NoticeScope;
  classRoomId: number | null;
  classRoomName: string | null;
  /** scope가 STUDENT일 때만 값이 있다 — 수업일 변경 승인이 만든 개인 공지다. */
  studentId: number | null;
  studentName: string | null;
  pinned: boolean;
  publishedAt: string | null;
  createdAt: string;
}

export const listTeacherNotices = (params: { page?: number }) =>
  get<PageResponse<TeacherNotice>>("/teacher/notices", params);

export const getTeacherNotice = (noticeId: number) =>
  get<TeacherNotice>(`/teacher/notices/${noticeId}`);

/** 만들면 초안이다. publish를 따로 호출해야 학생·학부모에게 보인다. */
export const createNotice = (body: {
  title: string;
  content: string;
  scope: NoticeScope;
  classRoomId: number | null;
  pinned: boolean;
}) => post<TeacherNotice>("/teacher/notices", body);

export const updateNotice = (
  noticeId: number,
  body: Partial<{
    title: string;
    content: string;
    pinned: boolean;
    scope: NoticeScope;
    classRoomId: number | null;
  }>,
) => patch<TeacherNotice>(`/teacher/notices/${noticeId}`, body);

export const publishNotice = (noticeId: number) =>
  post<TeacherNotice>(`/teacher/notices/${noticeId}/publish`);

export const deleteNotice = (noticeId: number) => del<void>(`/teacher/notices/${noticeId}`);
