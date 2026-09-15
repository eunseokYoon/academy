/**
 * 학생 화면과 선생님 화면이 같은 타입을 쓴다. 응답 모양이 같아서다 —
 * 다른 건 비공개 질문이 목록에 나오는지뿐이고 그건 서버가 거른다.
 */
export interface QnaPhoto {
  photoId: number;
  url: string;
}

/** 상태 필드가 없다. 답변 완료·미답변 개념을 만들지 않기로 확정했다. */
export interface QnaSummary {
  postId: number;
  title: string;
  authorName: string;
  classRoomId: number;
  classRoomName: string;
  isPublic: boolean;
  mine: boolean;
  hasPhoto: boolean;
  answerCount: number;
  createdAt: string;
}

export interface QnaAnswer {
  answerId: number;
  authorName: string;
  byTeacher: boolean;
  content: string;
  photos: QnaPhoto[];
  editable: boolean;
  createdAt: string;
}

export interface QnaDetail {
  postId: number;
  title: string;
  authorName: string;
  classRoomId: number;
  classRoomName: string;
  isPublic: boolean;
  content: string;
  photos: QnaPhoto[];
  editable: boolean;
  createdAt: string;
  answers: QnaAnswer[];
}

export interface QnaUploadUrl {
  uploadUrl: string;
  s3Key: string;
}

export const QNA_MAX_PHOTOS = 5;
