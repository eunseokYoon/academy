import { useState } from "react";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { issueAnswerUploadUrl } from "../api";

/** 서버 OnlineTestService 와 같다 — 장당 50MB, 최대 5장(2026-09-29). */
export const MAX_ANSWER_BYTES = 50 * 1024 * 1024;
export const MAX_ANSWER_FILES = 5;

/** 화면이 들고 있는 해설지 한 장. 이름은 이 화면에서 올린 것만 있다(서버는 이름을 안 둔다). */
export interface AnswerFileDraft {
  s3Key: string;
  name: string | null;
  /** 이미 저장된 것이면 서버가 준 열람 주소. */
  url?: string | null;
}

/**
 * 해설지는 서버를 거치지 않는다. presigned URL로 S3에 직접 PUT한다.
 *
 * <p>axios 인스턴스를 쓰지 않는다 — Authorization 헤더가 붙으면 서명이 어긋나 403이 난다.
 * Content-Type은 발급 때 보낸 값과 반드시 같아야 한다.
 */
async function uploadAnswerFile(file: File): Promise<string> {
  if (file.size > MAX_ANSWER_BYTES) throw new Error("해설지는 50MB까지입니다.");
  const { uploadUrl, s3Key } = await issueAnswerUploadUrl({
    contentType: file.type,
    bytes: file.size,
  });
  const response = await fetch(uploadUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": file.type },
  });
  if (!response.ok) throw new Error("해설지를 올리지 못했습니다. 다시 시도해 주세요.");
  return s3Key;
}

/**
 * 해설지 여러 장(2026-09-29). 파일을 고르면 바로 S3에 올리고 목록에 붙인다.
 * 빼기는 목록에서만 뺀다 — S3 삭제는 저장이 끝난 뒤 서버가 한다.
 */
export function AnswerFilesField({
  files,
  onChange,
  disabled = false,
}: {
  files: AnswerFileDraft[];
  onChange: (next: AnswerFileDraft[]) => void;
  disabled?: boolean;
}) {
  const [uploading, setUploading] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const room = MAX_ANSWER_FILES - files.length - uploading;

  async function pick(list: FileList | null) {
    const picked = Array.from(list ?? []);
    if (picked.length === 0) return;
    setError(null);
    if (picked.length > room) {
      setError(`해설지는 최대 ${MAX_ANSWER_FILES}장까지 올릴 수 있습니다.`);
      return;
    }
    setUploading((n) => n + picked.length);
    const added: AnswerFileDraft[] = [];
    try {
      // 한 장씩 올린다. 한꺼번에 올리면 실패했을 때 어느 파일인지 알 수 없다
      for (const file of picked) {
        added.push({ s3Key: await uploadAnswerFile(file), name: file.name });
      }
    } catch (e) {
      setError(errorMessage(e, "해설지 업로드에 실패했습니다."));
    } finally {
      setUploading((n) => n - picked.length);
      if (added.length > 0) onChange([...files, ...added]);
    }
  }

  return (
    <div className="space-y-1">
      <label className="block text-sm text-slate-700">
        해설지 (제출 후에만 학생에게 공개 · 최대 {MAX_ANSWER_FILES}장)
        <input
          type="file"
          multiple
          disabled={disabled || room <= 0}
          accept="application/pdf,image/jpeg,image/png,image/webp"
          onChange={(e) => {
            void pick(e.target.files);
            e.target.value = ""; // 같은 파일을 다시 고를 수 있게
          }}
          className="mt-1 w-full text-xs disabled:opacity-50"
        />
      </label>
      {uploading > 0 && <p className="text-xs text-slate-500">업로드 중…</p>}
      {files.length > 0 && (
        <ul className="space-y-1">
          {files.map((file, i) => (
            <li
              key={file.s3Key}
              className="flex items-center justify-between gap-2 rounded-lg bg-slate-50 px-2 py-1.5
                         text-xs"
            >
              {file.url ? (
                <a href={file.url} target="_blank" rel="noreferrer"
                  className="min-w-0 truncate text-slate-900 underline">
                  {file.name ?? `해설지 ${i + 1}`}
                </a>
              ) : (
                <span className="min-w-0 truncate text-slate-900">
                  {file.name ?? `해설지 ${i + 1}`}
                </span>
              )}
              <button
                type="button"
                disabled={disabled}
                onClick={() => onChange(files.filter((f) => f.s3Key !== file.s3Key))}
                className="shrink-0 text-slate-500 underline disabled:opacity-50"
              >
                빼기
              </button>
            </li>
          ))}
        </ul>
      )}
      <FormError message={error} />
    </div>
  );
}
