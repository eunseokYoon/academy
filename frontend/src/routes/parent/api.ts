import { get } from "../../shared/api/client";
import type { PageResponse } from "../../shared/api/types";
import type { AttendanceCalendar, AttendanceStatus, AttendanceSummary } from "../../shared/attendance/types";
import type { SubmissionStatus } from "../../shared/homework/types";
import type { NoticeSummary } from "../../shared/notice/api";
import type { ExamType, StudentExamSchedule, StudentScoreData } from "../../shared/score/types";

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

// ---------- 수업 레포트 (P-5) ----------

/**
 * 학생 화면(S-5)과 <b>같은 응답</b>이다. 영상 값만 null로 내려온다.
 *
 * <p>videoId·embedUrl이 null인 것은 "영상이 없다"가 아니라 "학부모는 못 본다"는 뜻이다.
 * 서버가 forParent 팩토리에서 고정해 보낸다. 프론트에서 이 값을 채워 재생을 붙이지 마라.
 */
export interface ParentLessonListItem {
  lessonId: number;
  lessonDate: string;
  title: string | null;
  classRoomName: string;
  /** 학부모 응답에서는 항상 null이다. false(영상 없음)와 다르다. */
  hasVideo: boolean | null;
  isNew: boolean;
  /** 학부모 응답에서는 항상 null이다. */
  viewed: boolean | null;
  homeworkTitle: string | null;
}

export interface ParentLessonDetail {
  lessonId: number;
  lessonDate: string;
  title: string | null;
  classRoomName: string;
  /** 학부모 응답에서는 항상 null이다. 프론트는 영상 영역을 그리지 않는다. */
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

export const getChildLessons = (
  studentId: number,
  params: { year?: number; month?: number; week?: number; page?: number },
) => get<PageResponse<ParentLessonListItem>>(`/parent/children/${studentId}/lessons`, params);

export const getChildLesson = (studentId: number, lessonId: number) =>
  get<ParentLessonDetail>(`/parent/children/${studentId}/lessons/${lessonId}`);

// ---------- 테스트 결과 (P-4) ----------

/**
 * 학부모 화면의 핵심이다. 표가 아니라 주차별 시계열이다.
 * 등수·백분위·반 평균은 응답에 없다.
 */
/** 학생 화면(S-7)과 같은 응답이다. 정기고사는 여기 내려오지 않는다. */
export const getChildScores = (studentId: number) =>
  get<StudentScoreData>(`/parent/children/${studentId}/scores`);

export const getChildExamSchedules = (studentId: number) =>
  get<StudentExamSchedule[]>(`/parent/children/${studentId}/exam-schedules`);

// ---------- 포털 홈 (P-1) ----------

/**
 * 여섯 카드를 <b>한 번의 호출</b>로 받는다. 자녀를 바꾸면 이 호출만 다시 하면 된다.
 *
 * <p>수업 제목·내용·영상, 숙제 사진·피드백, 자료실 필드는 없다.
 * 학부모는 "자녀가 했는지 여부"만 본다 — 응답에 추가하지 마라.
 *
 * <p>값이 없는 카드는 null이다. 0으로 오지 않는다.
 */
export interface ParentHome {
  student: {
    id: number;
    name: string;
    /** 자녀 번호 원본. 보호자 본인이 보는 값이라 가리지 않는다. 미가입 자녀는 null. */
    phone: string | null;
    classRooms: string[];
  };
  nextExam: {
    examType: ExamType;
    startDate: string;
    scopeNote: string | null;
    dDay: number;
  } | null;
  /** 날짜와 시각만 온다. 수업 내용은 학부모에게 노출하지 않는다. */
  nextLessonDate: string | null;
  /** 반에 그 요일 슬롯이 없으면 null이다. 그때는 날짜만 그린다. */
  nextLessonTime: string | null;
  notices: { totalCount: number; recent: NoticeSummary[] };
  pendingHomeworkCount: number;
  nextClinic: { clinicId: number; clinicDate: string; startTime: string; dDay: number } | null;
  thisMonthAttendance: AttendanceSummary;
}

export const getChildHome = (studentId: number) =>
  get<ParentHome>(`/parent/children/${studentId}/home`);
