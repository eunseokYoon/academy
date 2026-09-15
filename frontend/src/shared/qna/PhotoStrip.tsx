import { useEffect, useState } from "react";
import { Icon } from "../components/Icon";
import type { QnaPhoto } from "./types";

/**
 * 첨부 사진 가로 스트립. 탭하면 전체 화면으로 키운다.
 *
 * <p>presigned URL은 유효기간이 짧다. 화면을 오래 열어 두면 만료되므로
 * 이미지가 깨지면 새로고침하라는 안내 대신 그냥 빈 자리로 둔다 — 흔한 일이 아니다.
 */
export function PhotoStrip({ photos }: { photos: QnaPhoto[] }) {
  const [zoomed, setZoomed] = useState<string | null>(null);

  /**
   * 여는 버튼은 키보드로 누를 수 있는데 배경 클릭만으로 닫으면 키보드 사용자가
   * 오버레이에 갇힌다. Esc로 닫는 경로를 열려 있는 동안만 붙인다.
   */
  useEffect(() => {
    if (!zoomed) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setZoomed(null);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [zoomed]);

  if (photos.length === 0) return null;

  return (
    <>
      <div className="no-scrollbar -mx-1 mt-3 flex gap-2 overflow-x-auto px-1">
        {photos.map((photo) => (
          <button
            key={photo.photoId}
            type="button"
            onClick={() => setZoomed(photo.url)}
            className="h-24 w-24 shrink-0 overflow-hidden rounded-lg ring-1 ring-brand-100"
          >
            <img src={photo.url} alt="" className="h-full w-full object-cover" />
          </button>
        ))}
      </div>

      {zoomed && (
        <div
          role="presentation"
          onClick={() => setZoomed(null)}
          className="fixed inset-0 z-50 grid place-items-center bg-black/80 p-4"
        >
          <button
            type="button"
            onClick={() => setZoomed(null)}
            aria-label="닫기"
            className="absolute right-4 top-4 grid h-9 w-9 place-items-center rounded-full bg-black/50 text-white"
          >
            <Icon name="close" className="h-5 w-5" />
          </button>
          <img src={zoomed} alt="" className="max-h-full max-w-full object-contain" />
        </div>
      )}
    </>
  );
}
