import { createMaterial, issueMaterialUploadUrl } from "../../routes/teacher/api";
import type { MaterialCategory } from "./types";
import { ALLOWED_EXTENSIONS, MAX_MATERIAL_BYTES } from "./types";

/**
 * 자료 업로드. S3에 <b>한 번만 올리고</b> 선택한 반 수만큼 등록만 반복한다.
 *
 * <p>사진(숙제)과 달리 리사이즈가 없다 — pdf·hwp를 브라우저에서 줄일 방법이 없다.
 * 그래서 올리기 전에 확장자와 용량을 먼저 본다. 50MB를 다 올린 뒤 서버가 413을 주면
 * 선생님 시간만 버린다.
 *
 * <p>PUT은 axios 인스턴스를 쓰지 않는다. Authorization 헤더가 붙으면 서명이 어긋나 403이다.
 * Content-Type은 서버가 내려준 값과 반드시 같아야 한다.
 */
export async function uploadMaterial(
  file: File,
  meta: {
    title: string;
    category: MaterialCategory;
    year: number;
    month: number;
    week: number;
    /** 빈 배열이면 전체 공개(PUBLIC) 한 행을 만든다. */
    classRoomIds: number[];
  },
): Promise<number> {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!ALLOWED_EXTENSIONS.includes(extension as (typeof ALLOWED_EXTENSIONS)[number])) {
    throw new Error(`${ALLOWED_EXTENSIONS.join(" · ")} 형식만 올릴 수 있습니다.`);
  }
  if (file.size > MAX_MATERIAL_BYTES) {
    const mb = Math.round(file.size / (1024 * 1024));
    throw new Error(`자료는 50MB까지입니다. 고른 파일은 ${mb}MB입니다.`);
  }

  const { uploadUrl, s3Key, contentType } = await issueMaterialUploadUrl({
    fileName: file.name,
    bytes: file.size,
  });

  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": contentType },
  });
  if (!response.ok) {
    throw new Error("파일을 올리지 못했습니다. 다시 시도해 주세요.");
  }

  const common = {
    title: meta.title,
    category: meta.category,
    s3Key,
    fileName: file.name,
    bytes: file.size,
    year: meta.year,
    month: meta.month,
    week: meta.week,
  };

  if (meta.classRoomIds.length === 0) {
    await createMaterial({ ...common, classRoomId: null, visibility: "PUBLIC" });
    return 1;
  }

  // 반마다 한 행이다. 학교·학년 개념이 없어 서버에서 한 번에 묶을 수 없다
  for (const classRoomId of meta.classRoomIds) {
    await createMaterial({ ...common, classRoomId, visibility: "CLASS" });
  }
  return meta.classRoomIds.length;
}
