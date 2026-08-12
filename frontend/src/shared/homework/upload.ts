import {
  issueUploadUrl,
  issueVideoUploadUrl,
  registerPhoto,
  registerVideo,
} from "../../routes/student/api";

const MAX_SIDE = 1600;
const QUALITY = 0.8;

/** 서버와 같은 값. 넘으면 올리기 전에 여기서 막아 헛된 업로드를 피한다. */
export const MAX_VIDEO_BYTES = 100 * 1024 * 1024;

const VIDEO_TYPES = ["video/mp4", "video/quicktime", "video/webm"];

/**
 * 업로드 전 브라우저에서 줄인다. <b>이 함수 하나가 연간 저장 비용의 대부분을 결정한다.</b>
 * 요즘 폰 사진은 장당 3~4MB라 원본 그대로면 연 200GB가 넘고,
 * 선생님이 T-7에서 넘길 때마다 로딩이 멈춘다.
 *
 * <p>imageOrientation: "from-image"가 없으면 EXIF 회전이 무시돼 사진이 눕는다.
 */
export async function resizeImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));

  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("이미지를 변환하지 못했습니다."))),
      "image/webp",
      QUALITY,
    );
  });
}

/**
 * 리사이즈 → S3 직접 PUT → 서버에 등록. 세 단계다.
 *
 * <p>PUT은 axios 인스턴스를 쓰지 않는다. Authorization 헤더가 붙으면 서명이 어긋나 403이 난다.
 * Content-Type은 발급 때 보낸 값과 반드시 같아야 한다.
 */
export async function uploadPhoto(
  homeworkId: number,
  file: File,
  sortOrder: number,
): Promise<{ photoId: number; photoCount: number }> {
  const blob = await resizeImage(file);
  const { uploadUrl, s3Key } = await issueUploadUrl(homeworkId, {
    contentType: "image/webp",
    bytes: blob.size,
  });

  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: blob,
    headers: { "Content-Type": "image/webp" },
  });
  if (!response.ok) {
    throw new Error("사진을 올리지 못했습니다. 다시 시도해 주세요.");
  }

  return registerPhoto(homeworkId, { s3Key, sortOrder, bytes: blob.size });
}

/**
 * 영상 업로드. 사진과 달리 <b>리사이즈 단계가 없다</b> —
 * 브라우저에서 영상을 압축할 방법이 없어 폰이 찍은 원본이 그대로 올라간다.
 *
 * <p>그래서 올리기 전에 용량을 먼저 본다. 100MB짜리를 다 올린 뒤 서버가 413을 주면
 * 모바일 데이터만 쓰고 끝난다.
 */
export async function uploadVideo(
  homeworkId: number,
  file: File,
): Promise<{ url: string; bytes: number | null }> {
  if (!VIDEO_TYPES.includes(file.type)) {
    throw new Error("mp4 · mov · webm 형식만 올릴 수 있습니다.");
  }
  if (file.size > MAX_VIDEO_BYTES) {
    const mb = Math.round(file.size / (1024 * 1024));
    throw new Error(`영상은 100MB까지입니다. 고른 영상은 ${mb}MB입니다. 더 짧게 찍어 주세요.`);
  }

  const { uploadUrl, s3Key } = await issueVideoUploadUrl(homeworkId, {
    contentType: file.type,
    bytes: file.size,
  });

  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": file.type },
  });
  if (!response.ok) {
    throw new Error("영상을 올리지 못했습니다. 다시 시도해 주세요.");
  }

  return registerVideo(homeworkId, { s3Key, bytes: file.size });
}
