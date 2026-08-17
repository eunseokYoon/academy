import { issueQnaUploadUrl } from "../../routes/student/api";
import { issueTeacherQnaUploadUrl } from "../../routes/teacher/api";
import { resizeImage } from "../media/resize";

/**
 * 리사이즈 → S3 직접 PUT → s3Key 반환. 세 단계 중 앞 둘이다.
 * 등록(세 번째)은 글·답글 작성 요청이 s3Keys 배열로 함께 한다.
 *
 * <p>PUT은 axios 인스턴스를 쓰지 않는다. Authorization 헤더가 붙으면 서명이 어긋나 403이다.
 * Content-Type은 발급 때 보낸 값과 반드시 같아야 한다.
 *
 * <p>영상은 받지 않는다. 숙제 영상 100MB 상한을 게시판이 우회하게 된다.
 */
export async function uploadQnaPhoto(
  role: "student" | "teacher",
  file: File,
): Promise<string> {
  const blob = await resizeImage(file);
  const issue = role === "teacher" ? issueTeacherQnaUploadUrl : issueQnaUploadUrl;
  const { uploadUrl, s3Key } = await issue({
    contentType: "image/webp",
    bytes: blob.size,
  });

  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": "image/webp" },
    body: blob,
  });
  if (!response.ok) {
    throw new Error("사진을 올리지 못했습니다. 다시 시도해 주세요.");
  }
  return s3Key;
}
