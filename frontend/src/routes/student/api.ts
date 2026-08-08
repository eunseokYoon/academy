import { del, get, post, put } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceCalendar, AttendanceStatus } from "../../shared/attendance/types";
import type {
  HomeworkKind,
  HomeworkResult,
  SubmissionStatus,
} from "../../shared/homework/types";
import type { MaterialCategory } from "../../shared/material/types";
import type { OnlineTestResult, OnlineTestTakeStatus } from "../../shared/onlinetest/types";
import type { ExamType, StudentExamSchedule, StudentScoreData } from "../../shared/score/types";
import type { LessonChangeRequest, LessonSlot } from "../../shared/lessonchange/types";

export type ReservationStatus = "RESERVED" | "CANCELED" | "MOVED";
export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface MyReservation {
  reservationId: number;
  status: ReservationStatus;
  /**
   * null이면 결석이 아니라 <b>아직 출석 확정 전</b>이다.
   * 캘린더의 PENDING과 같은 뜻이라 화면에서도 같은 회색 "미확인"으로 그린다.
   */
  attendStatus: AttendanceStatus | null;
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

// ---------- 수업일 변경 (S-9) ----------

/** 못 가는 회차로 고를 수 있는 내 수업. 오늘부터 한 달. */
export const listMyChangeableLessons = () =>
  get<LessonSlot[]>("/student/lesson-changes/my-lessons");

/** 대신 갈 수업 후보. 그 수업이 있는 주(월~일)의 다른 반 수업만 나온다. */
export const listLessonChangeCandidates = (fromLessonId: number) =>
  get<LessonSlot[]>("/student/lesson-changes/candidates", { fromLessonId });

export const listMyLessonChanges = () =>
  get<LessonChangeRequest[]>("/student/lesson-changes");

/**
 * 요청만 한다. 승인해도 <b>배정과 수업 자체는 바뀌지 않고</b> 공지가 한 건 발행될 뿐이다.
 * 원래 반 출석부에는 그 날이 그대로 남는다.
 */
export const requestLessonChange = (body: {
  fromLessonId: number;
  toLessonId: number;
  reason: string;
}) => post<LessonChangeRequest>("/student/lesson-changes", body);

// ---------- 숙제 (S-2 · S-3 · S-4) ----------

export interface StudentHomeworkListItem {
  homeworkId: number;
  title: string;
  classRoomName: string;
  kind: HomeworkKind;
  /** GRID 열은 어느 수업 숙제인지 보여준다. ONLINE은 수업이 없을 수 있다. */
  lessonDate: string | null;
  /** null이면 선생님이 아직 채점하지 않았다. 0%가 아니다. */
  result: HomeworkResult | null;
  completionRate: number | null;
  resolvedByResubmission: boolean;
  /**
   * 제출 화면을 여는 <b>유일한 근거</b>다. 서버도 같은 기준으로 막으므로
   * 화면에서 버튼을 그리지 않는 건 안내일 뿐이고, 뚫려도 서버가 409를 낸다.
   */
  resubmitRequired: boolean;
  /** GRID 열은 재제출을 열기 전까지 마감이 없다. */
  dueAt: string | null;
  status: SubmissionStatus;
  isLate: boolean;
  photoCount: number;
  hasVideo: boolean;
  hasFeedback: boolean;
  /** 서버가 계산한다. 음수면 마감이 지난 것이고, 마감이 없으면 null이다. */
  remainingMinutes: number | null;
}

export interface StudentHomeworkDetail {
  homework: {
    id: number;
    title: string;
    description: string | null;
    kind: HomeworkKind;
    lessonDate: string | null;
    dueAt: string | null;
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

// ---------- 내 정보 (S-7 상단) ----------

export interface StudentMe {
  studentId: number;
  name: string;
  classRooms: { classRoomId: number; name: string }[];
  /** 서버가 마스킹한다 (010-****-2222). */
  phone: string | null;
  parentLinked: boolean;
}

export const getMe = () => get<StudentMe>("/student/me");

// ---------- 수업영상 · 레포트 (S-5) ----------

export interface StudentLessonListItem {
  lessonId: number;
  lessonDate: string;
  title: string | null;
  classRoomName: string;
  hasVideo: boolean;
  /** 최근 7일 내 공개된 수업이다. */
  isNew: boolean;
  viewed: boolean;
  homeworkTitle: string | null;
}

export interface StudentLessonDetail {
  lessonId: number;
  lessonDate: string;
  title: string | null;
  classRoomName: string;
  /** null이면 영상이 등록되지 않은 수업이다. 프론트는 영상 영역을 숨긴다. */
  videoId: string | null;
  embedUrl: string | null;
  content: string | null;
  keyPoints: string | null;
  nextPreview: string | null;
  homework: {
    homeworkId: number;
    title: string;
    description: string | null;
    dueAt: string;
    submissionStatus: SubmissionStatus | null;
  } | null;
  /** null이면 아직 출석 확정 전이다. 결석이 아니다. */
  attendanceStatus: AttendanceStatus | null;
}

export const listMyLessons = (params: {
  year?: number;
  month?: number;
  week?: number;
  page?: number;
}) => get<PageResponse<StudentLessonListItem>>("/student/lessons", params);

export const getMyLesson = (lessonId: number) =>
  get<StudentLessonDetail>(`/student/lessons/${lessonId}`);

/** 재생 시작(0) · 30초마다 · 이탈 시에만 호출한다. 매초 호출하지 마라. */
export const recordLessonView = (lessonId: number, watchSeconds: number) =>
  post<void>(`/student/lessons/${lessonId}/view`, { watchSeconds });

// ---------- 성적 · 시험 일정 (S-7) ----------

/** 학부모 화면(P-4)과 같은 응답이다. 정기고사는 여기 내려오지 않는다. */
export const getMyScores = () => get<StudentScoreData>("/student/scores");

export const listMyExamSchedules = () =>
  get<StudentExamSchedule[]>("/student/exam-schedules");

// ---------- 자료실 (S-8) ----------

/**
 * 자료실은 <b>학생 전용</b>이다. 학부모 화면에 같은 목록을 붙이지 마라.
 * s3Key는 내려오지 않는다 — 다운로드는 별도 호출로 presigned URL을 받는다.
 */
export interface StudentMaterial {
  materialId: number;
  title: string;
  category: MaterialCategory;
  fileName: string;
  bytes: number | null;
  year: number;
  month: number;
  week: number;
  createdAt: string;
}

export const listMyMaterials = (params: { category?: MaterialCategory; page?: number }) =>
  get<PageResponse<StudentMaterial>>("/student/materials", params);

/**
 * 유효기간이 5분이라 <b>받은 즉시 이동</b>시킨다. 목록에 미리 담아두면 전부 만료된다.
 * 목록에 없는 materialId로 호출하면 403이다.
 */
export const getMaterialDownloadUrl = (materialId: number) =>
  get<{ downloadUrl: string; fileName: string; expiresIn: number }>(
    `/student/materials/${materialId}/download-url`,
  );

// ---------- 홈 (S-1) ----------

/**
 * 여러 도메인을 <b>한 번의 호출</b>로 받는다. 카드별로 나눠 부르지 마라.
 * 값이 없는 카드는 null로 내려온다 (0이 아니다).
 */
export interface StudentHome {
  student: { name: string };
  nextLesson: {
    lessonDate: string;
    /** 반에 그 요일 슬롯이 없으면 null이다. 그때는 날짜만 그린다. */
    startTime: string | null;
    dDay: number;
    classRoomName: string;
  } | null;
  nextExam: {
    examType: ExamType;
    startDate: string;
    scopeNote: string | null;
    dDay: number;
  } | null;
  /** 마감 지난 미제출도 들어 있다. 마감 이른 순이다. */
  currentHomeworks: {
    homeworkId: number;
    title: string;
    dueAt: string;
    status: SubmissionStatus;
    /** 음수면 마감이 지난 것이다. */
    remainingMinutes: number;
  }[];
  noticeCount: number;
}

export const getStudentHome = () => get<StudentHome>("/student/home");

// ---------- 온라인 테스트 (S-10) ----------

/** 정답도 해설지도 내려오지 않는다. 서버 DTO에 필드 자체가 없다. */
export interface StudentOnlineTestListItem {
  testId: number;
  title: string;
  classRoomName: string;
  questionCount: number;
  closesAt: string | null;
  /** 서버가 계산한다. 음수면 마감 경과, null이면 마감 없음. */
  remainingMinutes: number | null;
  status: OnlineTestTakeStatus;
  answeredCount: number;
}

export interface OnlineTestTake {
  testId: number;
  title: string;
  classRoomName: string;
  questionCount: number;
  choiceCount: number;
  closesAt: string | null;
  /** 임시 저장된 답. 안 푼 문항은 null이다. */
  chosenChoices: (number | null)[];
  status: OnlineTestTakeStatus;
}

export const listMyOnlineTests = () =>
  get<StudentOnlineTestListItem[]>("/student/online-tests");

export const getOnlineTestToTake = (testId: number) =>
  get<OnlineTestTake>(`/student/online-tests/${testId}`);

/** 임시 저장은 서버에 한다. 브라우저가 닫혀도 답이 남아야 한다. */
export const saveOnlineTestAnswers = (testId: number, chosenChoices: (number | null)[]) =>
  put<void>(`/student/online-tests/${testId}/answers`, { chosenChoices });

/** 본문 없음. 마지막으로 저장한 답안으로 채점한다. */
export const submitOnlineTest = (testId: number) =>
  post<OnlineTestResult>(`/student/online-tests/${testId}/submit`);

export const getOnlineTestResult = (testId: number) =>
  get<OnlineTestResult>(`/student/online-tests/${testId}/result`);
