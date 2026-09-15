import type { PageResponse } from "../api/types";

/**
 * 학생 본인의 수강 후기. `GET /api/student/reviews/me`가 아직 안 썼으면 null을
 * 내려준다 — 그래서 이 타입 자체는 항상 값이 있는 모양이고, 없음은 호출부가
 * `MyReview | null`로 감싼다.
 */
export interface MyReview {
  reviewId: number;
  /** 0.5 단위, 0.5~5.0. */
  rating: number;
  content: string;
  updatedAt: string;
}

/** 선생님이 보는 후기 한 건. */
export interface TeacherReview {
  reviewId: number;
  /** 0.5 단위, 0.5~5.0. */
  rating: number;
  content: string;
  studentName: string;
  /** 후기를 쓴 학생이 지금 어느 반에도 재원 중이 아니면 null이다. */
  classRoomName: string | null;
  createdAt: string;
}

/**
 * `GET /api/teacher/reviews` 응답. `averageRating`은 후기가 하나도 없으면
 * **null**이다 — 0으로 내려오지 않는다. 별점 0개로 그리면 "다 별로였다"처럼
 * 보이니 화면에서도 null과 0을 같은 값으로 다루지 마라.
 */
export interface ReviewList {
  averageRating: number | null;
  totalCount: number;
  reviews: PageResponse<TeacherReview>;
}
