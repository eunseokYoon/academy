import { useState } from "react";
import type { QnaPhoto } from "./types";

/**
 * 첨부 사진 가로 스트립. 탭하면 전체 화면으로 키운다.
 *
 * <p>presigned URL은 유효기간이 짧다. 화면을 오래 열어 두면 만료되므로
 * 이미지가 깨지면 새로고침하라는 안내 대신 그냥 빈 자리로 둔다 — 흔한 일이 아니다.
 */
export function PhotoStrip({ photos }: { photos: QnaPhoto[] }) {
  const [zoomed, setZoomed] = useState<string | null>(null);

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
          <img src={zoomed} alt="" className="max-h-full max-w-full object-contain" />
        </div>
      )}
    </>
  );
}
