import { del, get, patch, post, put } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceCalendar, AttendanceStatus } from "../../shared/attendance/types";
import type {
  HomeworkKind,
  HomeworkResult,
  SubmissionStatus,
} from "../../shared/homework/types";
import type { MyReview } from "../../shared/review/types";
import type { NoticeSummary } from "../../shared/notice/api";
import type { OnlineTestResult, OnlineTestTakeStatus } from "../../shared/onlinetest/types";
import type { ExamType, StudentExamSchedule, StudentScoreData } from "../../shared/score/types";
import type { LessonChangeRequest, LessonSlot } from "../../shared/lessonchange/types";
import type { QnaDetail, QnaSummary, QnaUploadUrl } from "../../shared/qna/types";

export type ReservationStatus = "RESERVED" | "CANCELED" | "MOVED";
export interface MyReservation {
  reservationId: number;
  status: ReservationStatus;
  /** 내가 고른 도착 시각. "17:00" 형식이다. */
  arrivalTime: string;
  /**
   * null이면 결석이 아니라 <b>아직 출석 확정 전</b>이다.
   * 캘린더의 PENDING과 같은 뜻이라 화면에서도 같은 회색 "미확인"으로 그린다.
   */
  attendStatus: AttendanceStatus | null;
}

/** 다른 학생 이름은 내려오지 않는다. 인원 수만이다. */
export interface StudentClinic {
  clinicId: number;
  clinicDate: string;
  startTime: string;
  endTime: string;
  /**
   * 고를 수 있는 도착 시각. <b>서버가 계산해 내려준다</b> —
   * 시작·종료로 여기서 다시 만들면 "마지막 슬롯은 종료 1시간 전" 규칙이 두 곳으로 갈라진다.
   */
  slots: string[];
  capacity: number | null;
  reservedCount: number;
  /** 서버가 계산한다. capacity가 null이면 항상 false다. 정원은 클리닉 전체 기준이다 */
  full: boolean;
  /**
   * "8월 2주". 화면이 주차별로 묶는 데 쓴다.
   * <b>날짜로 여기서 다시 만들지 마라</b> — 수업·성적이 쓰는 주차 계산과 갈라진다.
   */
  weekLabel: string;
  myReservation: MyReservation | null;
}

// ---------- 출석 (S-6) ----------

export const getMyAttendances = (year: number, month: number) =>
  get<AttendanceCalendar>("/student/attendances", { year, month });

// ---------- 클리닉 (S-9) ----------

export const listMyClinics = (from: string, to: string) =>
  get<StudentClinic[]>("/student/clinics", { from, to });

interface ReservationResult {
  reservationId: number;
  clinicId: number;
  arrivalTime: string;
  status: ReservationStatus;
}

export const reserveClinic = (clinicId: number, arrivalTime: string) =>
  post<ReservationResult>(`/student/clinics/${clinicId}/reservation`, { arrivalTime });

/**
 * 도착 시각 변경 · 다른 클리닉으로 이동. <b>선생님 승인이 없다</b> — 즉시 반영된다.
 * targetClinicId를 null로 두면 같은 클리닉 안에서 시각만 바꾼다.
 * reason은 필수다. 이 문장이 선생님에게 남는 유일한 설명이다.
 *
 * <p><b>취소 API는 없다</b>(2026-08-10 확정). 못 가면 다른 시각으로 옮기고,
 * 아예 빠져야 하면 선생님이 배정을 해제한다.
 */
export const changeClinicReservation = (
  clinicId: number,
  body: { targetClinicId: number | null; arrivalTime: string; reason: string },
) => patch<ReservationResult>(`/student/clinics/${clinicId}/reservation`, body);


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
  /**
   * 제출 화면(사진·영상 추가, 제출 버튼)을 여는 <b>유일한 근거</b>다.
   * homework.kind·submission.status·homework.dueAt만으로는 판정할 수 없어 서버가 계산해 내려준다.
   */
  resubmitRequired: boolean;
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
    kind: HomeworkKind;
    /** GRID 열은 재제출을 열기 전까지 마감이 없다. */
    dueAt: string | null;
    /**
     * 오프라인 채점 축. GRID면 이걸로 그려라 —
     * submissionStatus는 ⭕를 받아도 NOT_SUBMITTED로 남아서 "미제출"이 뜬다.
     */
    result: HomeworkResult | null;
    completionRate: number | null;
    resolvedByResubmission: boolean;
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

// ---------- 성적 · 시험 일정 (S-7) ----------

/** 학부모 화면(P-4)과 같은 응답이다. 정기고사는 여기 내려오지 않는다. */
export const getMyScores = () => get<StudentScoreData>("/student/scores");

export const listMyExamSchedules = () =>
  get<StudentExamSchedule[]>("/student/exam-schedules");

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
  /**
   * 가장 최근에 뭔가 적힌 지난 수업. 아무것도 안 적힌 수업만 있으면 null이다.
   * <b>영상은 홈에서 재생하지 않는다</b> — embedUrl이 있으면 상세로 보내는 버튼만 그린다.
   */
  lastLesson: {
    lessonId: number;
    lessonDate: string;
    title: string | null;
    videoId: string | null;
    embedUrl: string | null;
    content: string | null;
    nextPreview: string | null;
  } | null;
  /** 학부모 홈과 같은 블록이다. recent는 배너에 펼치는 상단 몇 건. */
  notices: { totalCount: number; recent: NoticeSummary[] };
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

// ---------- S-9 질의응답 ----------

export const fetchQnaList = (params: { classRoomId?: number; page?: number }) =>
  get<PageResponse<QnaSummary>>("/student/qna", params);

export const fetchQnaDetail = (postId: number) =>
  get<QnaDetail>(`/student/qna/${postId}`);

export const createQna = (body: {
  classRoomId: number;
  title: string;
  content: string;
  isPublic: boolean;
  s3Keys: string[];
}) => post<number>("/student/qna", body);

export const answerQna = (postId: number, body: { content: string; s3Keys: string[] }) =>
  post<number>(`/student/qna/${postId}/comments`, body);

/** 질문과 답글 공통이다. 답글이면 title·isPublic을 빼야 400이 안 난다. */
export const updateQna = (
  id: number,
  body: { title?: string; content: string; isPublic?: boolean },
) => patch<void>(`/student/qna/${id}`, body);

export const deleteQna = (id: number) => del<void>(`/student/qna/${id}`);

export const issueQnaUploadUrl = (body: { contentType: string; bytes: number }) =>
  post<QnaUploadUrl>("/student/qna/photos/upload-url", body);

// ---------- 수강 후기 ----------

/** 아직 후기를 안 썼으면 data가 null이다. 404가 아니다 */
export const fetchMyReview = () => get<MyReview | null>("/student/reviews/me");

export const createReview = (body: { rating: number; content: string }) =>
  post<MyReview>("/student/reviews", body);

export const updateReview = (body: { rating: number; content: string }) =>
  patch<MyReview>("/student/reviews/me", body);

export const deleteReview = () => del<void>("/student/reviews/me");
