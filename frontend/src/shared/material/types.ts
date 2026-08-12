export type MaterialCategory = "LESSON" | "TEXTBOOK" | "PAST_EXAM" | "ETC";

/**
 * 공개 범위는 차원이 둘뿐이다.
 * PUBLIC은 반 제한이 없다는 뜻이고, "누구나"가 아니다 — 로그인한 재원생만 본다.
 */
export type MaterialVisibility = "PUBLIC" | "CLASS";

export const CATEGORY_LABELS: Record<MaterialCategory, string> = {
  LESSON: "수업자료",
  TEXTBOOK: "교재",
  PAST_EXAM: "기출",
  ETC: "기타",
};

/** 허용 확장자. 서버와 같은 목록이다. exe·sh·bat·js·html은 없다. */
export const ALLOWED_EXTENSIONS = [
  "pdf",
  "hwp",
  "hwpx",
  "docx",
  "xlsx",
  "pptx",
  "zip",
  "jpg",
  "jpeg",
  "png",
] as const;

/** 서버와 같은 값. 넘으면 올리기 전에 여기서 막아 헛된 업로드를 피한다. */
export const MAX_MATERIAL_BYTES = 50 * 1024 * 1024;

export function formatBytes(bytes: number | null): string {
  if (bytes === null) return "";
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}
