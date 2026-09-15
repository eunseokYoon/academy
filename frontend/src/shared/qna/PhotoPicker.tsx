import { useEffect, useRef, useState } from "react";
import { QNA_MAX_PHOTOS } from "./types";
import { uploadQnaPhoto } from "./upload";

/**
 * 첨부 사진 고르기. 고르는 즉시 리사이즈해서 S3에 올리고 s3Key만 부모에게 준다.
 *
 * <p>글을 저장하기 전에 올라가므로, 쓰다 만 사진이 S3에 남을 수 있다. 자료실과 같은 방식이고
 * 200명 규모에서 문제가 되는 양이 아니다.
 *
 * <p>미리보기는 로컬 objectURL이다. 올라간 파일을 다시 받아 보여줄 이유가 없다.
 */
export function PhotoPicker({
  role,
  value,
  onChange,
}: {
  role: "student" | "teacher";
  value: string[];
  onChange: (s3Keys: string[]) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previews, setPreviews] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const full = value.length >= QNA_MAX_PHOTOS;

  /**
   * 부모가 제출 후 value를 []로 비우는 경로가 있다(답글 폼). 그때 previews만 남으면
   * 두 배열의 인덱스가 어긋나서, 남아 있는 옛 썸네일의 ✕가 새로 올린 사진을 지운다.
   * 컴포넌트가 언마운트되지 않으므로 useState 초기값으로는 못 막는다.
   */
  useEffect(() => {
    if (value.length === 0) setPreviews([]);
  }, [value.length]);

  async function handlePick(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);
    setBusy(true);
    try {
      const room = QNA_MAX_PHOTOS - value.length;
      const picked = Array.from(files).slice(0, room);
      const keys: string[] = [];
      const urls: string[] = [];
      for (const file of picked) {
        keys.push(await uploadQnaPhoto(role, file));
        urls.push(URL.createObjectURL(file));
      }
      onChange([...value, ...keys]);
      setPreviews((prev) => [...prev, ...urls]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "사진을 올리지 못했습니다.");
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
    setPreviews((prev) => prev.filter((_, i) => i !== index));
  }

  return (
    <div className="mt-3">
      <div className="flex flex-wrap gap-2">
        {previews.map((url, index) => (
          <div key={url} className="relative h-20 w-20">
            <img
              src={url}
              alt=""
              className="h-full w-full rounded-lg object-cover ring-1 ring-brand-100"
            />
            <button
              type="button"
              onClick={() => remove(index)}
              aria-label="사진 빼기"
              className="absolute -right-1.5 -top-1.5 grid h-6 w-6 place-items-center
                         rounded-full bg-brand-900 text-xs text-white"
            >
              ✕
            </button>
          </div>
        ))}

        {!full && (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            disabled={busy}
            className="grid h-20 w-20 place-items-center rounded-lg border border-dashed
                       border-brand-200 text-xs text-brand-600 disabled:opacity-50"
          >
            {busy ? "올리는 중" : "사진 추가"}
          </button>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        hidden
        onChange={(e) => handlePick(e.target.files)}
      />

      <p className="mt-1.5 text-xs text-brand-500">사진은 최대 {QNA_MAX_PHOTOS}장입니다.</p>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  );
}
