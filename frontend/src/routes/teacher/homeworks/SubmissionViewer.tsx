import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { SUBMISSION_LABELS } from "../../../shared/homework/types";
import { getSubmission, markSubmissionNotDone } from "../api";

interface Props {
  submissionId: number;
  onNavigate: (submissionId: number) => void;
  onClose: () => void;
}

/**
 * T-7 상세 뷰어. <b>보기 전용이다.</b>
 *
 * <p>확인·피드백 단계는 없앴다(2026-08-09). GRID 재제출은 학생이 내는 순간 ⭕가 되므로
 * 선생님이 눌러야 하는 것이 없고, 이 화면은 낸 사진·영상을 훑기 위해 남아 있다.
 * <b>입력창을 다시 붙이지 마라</b> — 강사가 1명이라 200명분 확인 절차가 그대로 병목이 된다.
 *
 * <p>이전·다음은 낸 학생을 이름순으로 훑는다. 목록으로 돌아가지 않고 옆 사람으로 넘어가는
 * 이 동선이 없으면 반 전체를 보는 데 클릭이 두 배로 든다.
 */
export default function SubmissionViewer({ submissionId, onNavigate, onClose }: Props) {
  const [photoIndex, setPhotoIndex] = useState(0);
  const [zoomed, setZoomed] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [marking, setMarking] = useState(false);
  const queryClient = useQueryClient();

  const submission = useQuery({
    queryKey: ["teacher", "submission", submissionId],
    queryFn: () => getSubmission(submissionId),
  });
  const data = submission.data;

  // 제출물이 바뀌면 사진 위치를 처음으로 되돌린다. 안 그러면 사진이 3장인 사람에서
  // 1장인 사람으로 넘어갈 때 빈 화면이 뜬다
  useEffect(() => {
    setPhotoIndex(0);
    setZoomed(false);
  }, [data?.submissionId]);

  function confirmMarkNotDone() {
    setConfirming(true);
  }

  async function doMarkNotDone() {
    setMarking(true);
    try {
      await markSubmissionNotDone(data!.submissionId);
      /* 그리드와 목록이 같은 칸을 보고 있다. 둘 다 무효화해야 ⭕가 ❌로 바뀐다 */
      await queryClient.invalidateQueries({ queryKey: ["teacher", "homework-grid"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "submissions"] });
      setConfirming(false);
      onClose();
    } finally {
      setMarking(false);
    }
  }

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
        {data.canMarkNotDone && (
          <button
            type="button"
            onClick={() => void confirmMarkNotDone()}
            disabled={marking}
            className="shrink-0 rounded-lg border border-red-400 px-2 py-1 text-xs
                       font-semibold text-red-300 disabled:opacity-50"
          >
            {marking ? "처리 중…" : "미흡"}
          </button>
        )}
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

      {/* 학생 사이 이동. 사진 넘기기(위 ‹ ›)와 헷갈리지 않게 아래 흰 바에 이름으로 둔다 */}
      <div className="flex gap-2 bg-white p-3">
        <button
          type="button"
          onClick={() => onNavigate(data.prevSubmissionId!)}
          disabled={data.prevSubmissionId === null}
          className="flex-1 rounded-lg border border-slate-300 px-4 py-3 text-sm font-medium
                     text-slate-700 disabled:opacity-40"
        >
          ← 이전 학생
        </button>
        <button
          type="button"
          onClick={() => onNavigate(data.nextSubmissionId!)}
          disabled={data.nextSubmissionId === null}
          className="flex-1 rounded-lg border border-slate-300 px-4 py-3 text-sm font-medium
                     text-slate-700 disabled:opacity-40"
        >
          다음 학생 →
        </button>
      </div>

      {confirming && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/70 p-6">
          <div className="w-full max-w-xs rounded-2xl bg-white p-5">
            <p className="text-[15px] font-bold text-brand-900">미흡으로 되돌릴까요?</p>
            <p className="mt-2 text-[13px] leading-relaxed text-slate-600">
              사진 {data.photos.length}장{data.video ? "과 영상" : ""}이 삭제됩니다.
              <br />
              <b className="text-red-600">되돌릴 수 없습니다.</b>
              <br />
              학생은 이 숙제를 다시 제출해야 합니다.
            </p>
            <div className="mt-4 flex gap-2">
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="flex-1 rounded-xl border border-slate-300 py-2.5 text-sm font-bold
                           text-slate-600"
              >
                취소
              </button>
              <button
                type="button"
                onClick={() => void doMarkNotDone()}
                disabled={marking}
                className="flex-1 rounded-xl bg-red-600 py-2.5 text-sm font-bold text-white
                           disabled:opacity-50"
              >
                되돌리기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
