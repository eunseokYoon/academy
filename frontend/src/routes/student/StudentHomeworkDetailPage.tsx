import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { SUBMISSION_LABELS, formatDueAt } from "../../shared/homework/types";
import { uploadPhoto, uploadVideo } from "../../shared/homework/upload";
import { deletePhoto, deleteVideo, getMyHomework, submitHomework } from "./api";

const MAX_PHOTOS = 10;

/** 한 장씩 상태를 들고 있어야 실패한 것만 다시 올릴 수 있다. */
interface Uploading {
  key: string;
  name: string;
  file: File;
  error: string | null;
}

/**
 * S-3 제출 + S-4 상세·피드백. 상태에 따라 한 화면이 두 역할을 한다.
 *
 * <p>선생님이 확인을 마치면(CHECKED) 수정이 막힌다. 본 내용이 뒤바뀌면
 * 피드백이 엉뚱한 사진에 붙는다.
 */
export default function StudentHomeworkDetailPage() {
  const { homeworkId } = useParams();
  const id = Number(homeworkId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const videoInput = useRef<HTMLInputElement>(null);

  const [uploading, setUploading] = useState<Uploading[]>([]);
  const [videoUploading, setVideoUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const detail = useQuery({
    queryKey: ["student", "homework", id],
    queryFn: () => getMyHomework(id),
  });

  const removePhoto = useMutation({
    mutationFn: (photoId: number) => deletePhoto(id, photoId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["student", "homework", id] }),
    onError: (e) => setError(errorMessage(e, "사진을 지우지 못했습니다.")),
  });

  const removeVideo = useMutation({
    mutationFn: () => deleteVideo(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["student", "homework", id] }),
    onError: (e) => setError(errorMessage(e, "영상을 지우지 못했습니다.")),
  });

  const submit = useMutation({
    mutationFn: () => submitHomework(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["student", "homework", id] });
      await queryClient.invalidateQueries({ queryKey: ["student", "homeworks"] });
    },
    onError: (e) => setError(errorMessage(e, "제출하지 못했습니다.")),
  });

  if (detail.isPending) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }
  if (!detail.data) {
    return <p className="text-sm text-slate-500">숙제를 찾을 수 없습니다.</p>;
  }

  const { homework, submission, feedback } = detail.data;
  const locked = submission.status === "CHECKED";
  const photoCount = submission.photos.length;

  /**
   * 고른 즉시 한 장씩 리사이즈해 올린다. 한 장이 실패해도 나머지는 그대로 진행하고,
   * 실패한 것만 다시 시도할 수 있게 목록에 남긴다.
   */
  async function handleFiles(files: FileList) {
    setError(null);
    const room = MAX_PHOTOS - photoCount - uploading.length;
    const picked = Array.from(files).slice(0, Math.max(0, room));
    if (picked.length < files.length) {
      setError(`사진은 최대 ${MAX_PHOTOS}장까지 올릴 수 있습니다.`);
    }

    const queued: Uploading[] = picked.map((file, index) => ({
      key: `${Date.now()}-${index}-${file.name}`,
      name: file.name,
      file,
      error: null,
    }));
    setUploading((current) => [...current, ...queued]);

    for (const [index, item] of queued.entries()) {
      try {
        await uploadPhoto(id, item.file, photoCount + index + 1);
        setUploading((current) => current.filter((row) => row.key !== item.key));
      } catch (e) {
        const message = errorMessage(e, "사진을 올리지 못했습니다.");
        setUploading((current) =>
          current.map((row) => (row.key === item.key ? { ...row, error: message } : row)),
        );
      }
    }
    await queryClient.invalidateQueries({ queryKey: ["student", "homework", id] });
  }

  /**
   * 영상은 리사이즈 단계가 없어 한 번에 올린다. 이미 있으면 서버가 덮어쓴다.
   * 100MB 초과·형식 오류는 올리기 전에 uploadVideo가 걸러 준다.
   */
  async function handleVideo(file: File) {
    setError(null);
    setVideoUploading(true);
    try {
      await uploadVideo(id, file);
      await queryClient.invalidateQueries({ queryKey: ["student", "homework", id] });
    } catch (e) {
      setError(e instanceof Error ? e.message : errorMessage(e, "영상을 올리지 못했습니다."));
    } finally {
      setVideoUploading(false);
    }
  }

  async function retry(item: Uploading) {
    setUploading((current) =>
      current.map((row) => (row.key === item.key ? { ...row, error: null } : row)),
    );
    try {
      await uploadPhoto(id, item.file, photoCount + 1);
      setUploading((current) => current.filter((row) => row.key !== item.key));
      await queryClient.invalidateQueries({ queryKey: ["student", "homework", id] });
    } catch (e) {
      const message = errorMessage(e, "사진을 올리지 못했습니다.");
      setUploading((current) =>
        current.map((row) => (row.key === item.key ? { ...row, error: message } : row)),
      );
    }
  }

  return (
    <div className="space-y-4">
      <button type="button" onClick={() => navigate("/student/homeworks")} className="text-sm text-slate-500">
        ← 숙제 목록
      </button>

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-start justify-between gap-2">
          <h2 className="text-lg font-semibold text-slate-900">{homework.title}</h2>
          <Badge tone={submission.status === "NOT_SUBMITTED" ? "warn" : "ok"}>
            {SUBMISSION_LABELS[submission.status]}
          </Badge>
        </div>
        <p className="mt-0.5 text-sm text-slate-500">
          {homework.classRoomName} · {formatDueAt(homework.dueAt)} 마감
        </p>
        {homework.description && (
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-700">{homework.description}</p>
        )}
        {submission.isLate && (
          <p className="mt-2 text-xs text-amber-700">마감 후에 제출했습니다.</p>
        )}
      </div>

      {feedback && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
          <p className="text-sm font-medium text-emerald-800">선생님 피드백</p>
          <p className="mt-1 whitespace-pre-wrap text-sm text-emerald-900">{feedback.content}</p>
        </div>
      )}

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-slate-700">
            사진 {photoCount}/{MAX_PHOTOS}
          </p>
          {!locked && (
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              disabled={photoCount + uploading.length >= MAX_PHOTOS}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700
                         disabled:opacity-40"
            >
              사진 추가
            </button>
          )}
        </div>

        <input
          ref={fileInput}
          type="file"
          accept="image/*"
          multiple
          hidden
          onChange={(e) => {
            if (e.target.files) void handleFiles(e.target.files);
            e.target.value = "";
          }}
        />

        <ul className="mt-3 grid grid-cols-3 gap-2">
          {submission.photos.map((photo) => (
            <li key={photo.photoId} className="relative">
              <img
                src={photo.url}
                alt="제출 사진"
                className="aspect-square w-full rounded-lg object-cover"
              />
              {!locked && (
                <button
                  type="button"
                  onClick={() => removePhoto.mutate(photo.photoId)}
                  className="absolute right-1 top-1 rounded-full bg-slate-900/70 px-2 py-0.5
                             text-xs text-white"
                  aria-label="사진 삭제"
                >
                  ✕
                </button>
              )}
            </li>
          ))}
          {/* 리사이즈+업로드에 몇 초가 걸린다. 아무 반응이 없으면 버튼을 여러 번 누른다 */}
          {uploading.map((item) => (
            <li
              key={item.key}
              className="flex aspect-square flex-col items-center justify-center gap-1
                         rounded-lg border border-dashed border-slate-300 p-1 text-center"
            >
              {item.error ? (
                <>
                  <span className="text-[10px] text-red-600">실패</span>
                  <button
                    type="button"
                    onClick={() => void retry(item)}
                    className="text-xs text-slate-700 underline"
                  >
                    다시 시도
                  </button>
                </>
              ) : (
                <span className="text-xs text-slate-400">올리는 중…</span>
              )}
            </li>
          ))}
        </ul>

      </div>

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-slate-700">영상 (1개까지)</p>
          {!locked && (
            <button
              type="button"
              onClick={() => videoInput.current?.click()}
              disabled={videoUploading}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-700
                         disabled:opacity-40"
            >
              {submission.video ? "영상 바꾸기" : "영상 추가"}
            </button>
          )}
        </div>

        <input
          ref={videoInput}
          type="file"
          accept="video/*"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void handleVideo(file);
            e.target.value = "";
          }}
        />

        {videoUploading ? (
          <p className="mt-3 text-center text-xs text-slate-400">
            영상을 올리는 중입니다. 사진보다 오래 걸립니다…
          </p>
        ) : submission.video ? (
          <div className="mt-3">
            {/* 트랜스코딩을 하지 않아 아이폰 원본(HEVC)은 일부 브라우저에서 재생이 안 될 수 있다 */}
            <video
              src={submission.video.url}
              controls
              playsInline
              preload="metadata"
              className="w-full rounded-lg bg-black"
            />
            {!locked && (
              <button
                type="button"
                onClick={() => removeVideo.mutate()}
                className="mt-2 w-full text-center text-xs text-slate-500 underline"
              >
                영상 삭제
              </button>
            )}
          </div>
        ) : (
          <p className="mt-3 text-center text-xs text-slate-400">
            100MB까지 올릴 수 있습니다. 폰으로 1분 안쪽 분량입니다.
          </p>
        )}
      </div>

      {photoCount === 0 && !submission.video && uploading.length === 0 && !videoUploading && (
        <p className="text-center text-xs text-slate-400">
          사진이나 영상을 하나 이상 올려야 제출할 수 있습니다.
        </p>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {locked ? (
        <p className="rounded-xl bg-slate-100 p-3 text-center text-sm text-slate-500">
          선생님이 확인을 마쳐 더 이상 수정할 수 없습니다.
        </p>
      ) : (
        <button
          type="button"
          onClick={() => submit.mutate()}
          disabled={
            (photoCount === 0 && !submission.video) ||
            submit.isPending ||
            uploading.length > 0 ||
            videoUploading
          }
          className="w-full rounded-lg bg-slate-900 px-4 py-3 text-base font-medium text-white
                     disabled:bg-slate-300"
        >
          {submit.isPending
            ? "제출 중…"
            : submission.status === "SUBMITTED"
              ? "다시 제출하기"
              : "제출하기"}
        </button>
      )}
    </div>
  );
}
