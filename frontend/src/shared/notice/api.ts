import { get, post } from "../api/client";
import type { PageResponse } from "../api/types";

/**
 * STUDENT는 수업일 변경 승인이 만드는 개인 공지다. 그 학생과 학부모만 본다.
 * 선생님이 손으로 쓰는 공지는 ALL·CLASS 둘뿐이라 작성 폼에는 이 값이 없다 —
 * 서버도 STUDENT로 오는 작성 요청을 400으로 막는다.
 */
export type NoticeScope = "ALL" | "CLASS" | "STUDENT";

export interface NoticeSummary {
  noticeId: number;
  title: string;
  pinned: boolean;
  /** 여기 오는 공지는 전부 발행된 것이라 null이 아니다. */
  publishedAt: string;
}

export interface NoticeDetail {
  noticeId: number;
  title: string;
  /** 사용자가 입력한 <b>일반 텍스트</b>다. HTML로 렌더링하지 마라. */
  content: string;
  pinned: boolean;
  publishedAt: string;
}

/**
 * 학생·학부모 공통 경로다. <b>studentId는 학부모가 자녀를 지정할 때만</b> 넘긴다.
 * 학생 본인은 생략하면 서버가 토큰에서 찾는다.
 *
 * <p>초안(publishedAt이 null)은 서버가 걸러내므로 목록에 오지 않는다.
 * 정렬은 고정 공지 우선 → 최신순이다.
 */
export const listNotices = (params: { studentId?: number; page?: number } = {}) =>
  get<PageResponse<NoticeSummary>>("/notices", params);

export const getNotice = (noticeId: number, studentId?: number) =>
  get<NoticeDetail>(`/notices/${noticeId}`, studentId ? { studentId } : undefined);

/**
 * 공지 첨부 업로드 URL 발급. 확장자·용량 검사는 서버(MaterialKeys 재사용)가 한다.
 * 응답의 contentType 그대로 PUT해야 서명이 맞는다.
 */
export const issueUploadUrl = (body: { fileName: string; bytes: number }) =>
  post<{ uploadUrl: string; s3Key: string; contentType: string }>(
    "/teacher/notices/attachments/upload-url",
    body,
  );
