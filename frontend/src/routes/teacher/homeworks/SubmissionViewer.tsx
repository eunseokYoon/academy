import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { SUBMISSION_LABELS } from "../../../shared/homework/types";
import { checkSubmission, createFeedback, getSubmission, updateFeedback } from "../api";

/** 200명에게 매번 새로 쓰는 건 불가능하다. 한 번에 넣을 문구를 버튼으로 둔다. */
const QUICK_PHRASES = ["잘했어요", "다시 확인 필요", "글씨 정성껏", "빠진 문제 있어요"];

interface Props {
  submissionId: number;
  onNavigate: (submissionId: number) => void;
  onClose: () => void;
}

/**
 * T-7 상세 뷰어. <b>"저장하고 다음"이 이 화면의 전부다.</b>
 * 저장 후 목록으로 돌아가지 않고 다음 미확인 제출물로 바로 넘어간다 —
 * 200명을 확인해야 하므로 이 동선이 없으면 실사용이 안 된다.
 */
export default function SubmissionViewer({ submissionId, onNavigate, onClose }: Props) {
  const queryClient = useQueryClient();
  const [content, setContent] = useState("");
  const [photoIndex, setPhotoIndex] = useState(0);
  const [zoomed, setZoomed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submission = useQuery({
    queryKey: ["teacher", "submission", submissionId],
    queryFn: () => getSubmission(submissionId),
  });
  const data = submission.data;

  // 제출물이 바뀌면 입력창과 사진 위치를 초기화한다.
  // 이미 쓴 피드백이 있으면 그것을 불러와 수정 모드가 된다
  useEffect(() => {
    setContent(data?.feedback?.content ?? "");
    setPhotoIndex(0);
    setZoomed(false);
    setError(null);
  }, [data?.submissionId, data?.feedback?.content]);

  const save = useMutation({
    mutationFn: async (goNext: boolean) => {
      const text = content.trim();
      if (text.length === 0) {
        await checkSubmission(submissionId);
      } else if (data?.feedback) {
        await updateFeedback(submissionId, text);
      } else {
        await createFeedback(submissionId, text);
      }
      return goNext;
    },
    onSuccess: async (goNext) => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "submissions"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "homeworks"] });
      const next = data?.nextSubmissionId ?? null;
      if (goNext && next !== null) {
        onNavigate(next);
      } else {
        onClose();
      }
    },
    onError: (e) => setError(errorMessage(e, "저장하지 못했습니다.")),
  });

  if (submission.isPending || !data) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60">
        <p className="text-sm text-white">불러오는 중…</p>
      </div>
    );
  }

  // 사진과 영상을 한 줄로 세워 좌우로 넘긴다. 영상은 항상 마지막이다
  const media: { key: string; url: string; video: boolean }[] = [
    ...data.photos.map((photo) => ({
      key: `photo-${photo.photoId}`,
      url: photo.url,
      video: false,
    })),
    ...(data.video ? [{ key: "video", url: data.video.url, video: true }] : []),
  ];
  const current = media[photoIndex];
  const isLast = data.nextSubmissionId === null;

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-slate-900">
      <header className="flex items-center justify-between gap-2 px-4 py-3 text-white">
        <div className="min-w-0">
          <p className="truncate text-base font-semibold">{data.studentName}</p>
          <p className="text-xs text-slate-300">
            {SUBMISSION_LABELS[data.status]}
            {data.isLate && " · 늦게 냄"}
            {media.length > 0 && ` · ${photoIndex + 1}/${media.length}`}
            {current?.video && " · 영상"}
          </p>
        </div>
        <button type="button" onClick={onClose} className="shrink-0 text-sm text-slate-300">
          닫기
        </button>
      </header>

      <div className="relative flex flex-1 items-center justify-center overflow-auto bg-black">
        {!current ? (
          <p className="text-sm text-slate-400">제출한 사진이 없습니다.</p>
        ) : current.video ? (
          // 트랜스코딩을 안 하므로 아이폰 원본(HEVC)은 재생이 안 될 수 있다.
          // 그때는 브라우저가 기본 오류 UI를 보여준다
          <video
            key={current.key}
            src={current.url}
            controls
            playsInline
            preload="metadata"
            className="max-h-full max-w-full"
          />
        ) : (
          <img
            src={current.url}
            alt={`${data.studentName} 제출 사진 ${photoIndex + 1}`}
            onClick={() => setZoomed((value) => !value)}
            className={
              zoomed
                ? "max-w-none cursor-zoom-out"
                : "max-h-full max-w-full cursor-zoom-in object-contain"
            }
            style={zoomed ? { width: "200%" } : undefined}
          />
        )}

        {media.length > 1 && (
          <>
            <button
              type="button"
              onClick={() => setPhotoIndex((i) => Math.max(0, i - 1))}
              disabled={photoIndex === 0}
              className="absolute left-2 rounded-full bg-white/20 px-3 py-2 text-lg text-white
                         disabled:opacity-30"
              aria-label="이전 사진"
            >
              ‹
            </button>
            <button
              type="button"
              onClick={() => setPhotoIndex((i) => Math.min(media.length - 1, i + 1))}
              disabled={photoIndex === media.length - 1}
              className="absolute right-2 rounded-full bg-white/20 px-3 py-2 text-lg text-white
                         disabled:opacity-30"
              aria-label="다음 사진"
            >
              ›
            </button>
          </>
        )}
      </div>

      <div className="space-y-2 bg-white p-3">
        <div className="flex flex-wrap gap-1">
          {QUICK_PHRASES.map((phrase) => (
            <button
              key={phrase}
              type="button"
              onClick={() => setContent((value) => (value ? `${value} ${phrase}` : phrase))}
              className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-slate-600"
            >
              {phrase}
            </button>
          ))}
        </div>

        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={2}
          placeholder="피드백 (비워 두면 확인 처리만 됩니다)"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-base outline-none
                     focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
        />
        {error && <p className="text-xs text-red-600">{error}</p>}

        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => save.mutate(false)}
            disabled={save.isPending}
            className="flex-1 rounded-lg border border-slate-300 px-4 py-3 text-sm font-medium
                       text-slate-700 disabled:opacity-50"
          >
            저장
          </button>
          <button
            type="button"
            onClick={() => save.mutate(true)}
            disabled={save.isPending || isLast}
            className="flex-[2] rounded-lg bg-slate-900 px-4 py-3 text-sm font-medium text-white
                       disabled:bg-slate-300"
          >
            {isLast ? "확인 대기 없음" : "저장하고 다음"}
          </button>
        </div>

        {data.prevSubmissionId !== null && (
          <button
            type="button"
            onClick={() => onNavigate(data.prevSubmissionId!)}
            className="w-full text-center text-xs text-slate-500 underline"
          >
            이전 제출물로
          </button>
        )}
      </div>
    </div>
  );
}
