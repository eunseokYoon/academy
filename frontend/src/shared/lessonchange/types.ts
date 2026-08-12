import { dayLabel } from "../date";

export type ChangeRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

/**
 * 수업일 변경 화면의 수업 한 칸. S-9(학생)과 T-13(선생님)이 같은 모양을 받는다.
 *
 * <p>수업 제목·영상·레포트는 여기 없다. 다른 반 수업까지 나오는 목록이라
 * 서버가 날짜·반 이름·시각만 내려준다. 필드를 늘려 달라고 하지 마라.
 *
 * <p>startTime은 null일 수 있다 — 그 반에 그 요일 슬롯이 없는 경우다.
 */
export interface LessonSlot {
  lessonId: number;
  classRoomId: number;
  classRoomName: string;
  lessonDate: string;
  startTime: string | null;
  endTime: string | null;
}

export interface LessonChangeRequest {
  requestId: number;
  studentId: number;
  studentName: string;
  from: LessonSlot;
  to: LessonSlot;
  /** 필수값이고 승인 공지 본문에 그대로 들어간다. */
  reason: string;
  status: ChangeRequestStatus;
  decidedAt: string | null;
  createdAt: string;
}

/**
 * "08-13 (목) A고 2학년 목요일반 19:00".
 * 시각은 없을 수 있다 — 그 반에 그 요일 슬롯이 없는 경우다. 없으면 빼고 쓴다.
 */
export function formatLessonSlot(lesson: LessonSlot): string {
  const time = lesson.startTime ? ` ${lesson.startTime.slice(0, 5)}` : "";
  return `${lesson.lessonDate.slice(5)} (${dayLabel(lesson.lessonDate)}) ${lesson.classRoomName}${time}`;
}
