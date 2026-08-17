/**
 * 숙제 제출과 질의응답 게시판이 같이 쓴다. 원래 shared/homework/upload.ts 안에 있었는데,
 * 게시판이 생기면서 두 벌이 될 뻔해 여기로 꺼냈다. 리사이즈 규칙이 갈라지면 한쪽만
 * 원본을 올리기 시작하고 저장 비용이 조용히 늘어난다.
 */

const MAX_SIDE = 1600;
const QUALITY = 0.8;

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
