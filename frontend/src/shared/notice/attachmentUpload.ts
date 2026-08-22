import { issueUploadUrl } from "./api";

/** 등록 요청(NoticeAttachmentRequest)과 같은 모양이다. */
export interface AttachmentDraft {
  s3Key: string;
  fileName: string;
  bytes: number;
}

/**
 * 공지 첨부 업로드. 발급 → S3 직접 PUT → 등록용 값 반환.
 * 파일은 서버를 거치지 않는다.
 *
 * <p>PUT은 axios 인스턴스를 쓰지 않는다. Authorization 헤더가 붙으면 서명이 어긋나 403이다.
 * Content-Type은 발급 때 받은 값과 반드시 같아야 한다.
 *
 * 등록(공지 생성·수정)은 호출부가 한다 — 공지가 아직 없을 수도 있어서다.
 */
export async function uploadNoticeAttachment(file: File): Promise<AttachmentDraft> {
  const { uploadUrl, s3Key, contentType } = await issueUploadUrl({
    fileName: file.name,
    bytes: file.size,
  });

  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body: file,
  });
  if (!response.ok) {
    throw new Error("파일을 올리지 못했습니다. 다시 시도해 주세요.");
  }

  return { s3Key, fileName: file.name, bytes: file.size };
}

export function formatBytes(bytes: number | null): string {
  if (bytes === null) return "";
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}
