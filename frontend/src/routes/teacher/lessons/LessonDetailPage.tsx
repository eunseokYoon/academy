import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextAreaField } from "../../../shared/components/TextAreaField";
import { TextField } from "../../../shared/components/TextField";
import {
  deleteLesson,
  getLesson,
  getLessonViews,
  publishLesson,
  updateLesson,
} from "../api";
import type { LessonDetail } from "../api";
import { formatWeek } from "../format";

export default function LessonDetailPage() {
  const lessonId = Number(useParams().lessonId);
  const queryClient = useQueryClient();

  const { data, isPending } = useQuery({
    queryKey: ["teacher", "lessons", lessonId],
    queryFn: () => getLesson(lessonId),
  });

  function refresh(updated?: LessonDetail) {
    if (updated) queryClient.setQueryData(["teacher", "lessons", lessonId], updated);
    return queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
  }

  if (isPending || !data) return <p className="text-sm text-slate-400">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-lg font-semibold text-slate-900">{data.lessonDate}</h2>
          {data.publishedAt ? <Badge tone="ok">공개됨</Badge> : <Badge tone="warn">미공개</Badge>}
          {data.attendanceStatus === "PENDING" && <Badge tone="neutral">출석 미확정</Badge>}
        </div>
        <p className="mt-0.5 text-sm text-slate-500">
          {data.classRoomName} · {formatWeek(data.year, data.month, data.week)}
        </p>
      </div>

      <ContentForm lesson={data} onDone={refresh} />
      <PublishSection lesson={data} onDone={refresh} />
      {/* 공개 전에는 학생이 볼 수 없으니 시청 기록도 생기지 않는다 */}
      {data.publishedAt && data.videoUrl && <ViewSection lessonId={lessonId} />}
      <DeleteSection lesson={data} />

      <Link to="/teacher/lessons" className="block py-2 text-sm text-slate-500 underline">
        수업 목록으로
      </Link>
    </div>
  );
}

interface Props {
  lesson: LessonDetail;
  onDone: (updated?: LessonDetail) => Promise<unknown>;
}

function ContentForm({ lesson, onDone }: Props) {
  const [week, setWeek] = useState(String(lesson.week));
  const [title, setTitle] = useState(lesson.title ?? "");
  const [videoUrl, setVideoUrl] = useState(lesson.videoUrl ?? "");
  const [content, setContent] = useState(lesson.content ?? "");
  const [keyPoints, setKeyPoints] = useState(lesson.keyPoints ?? "");
  const [nextPreview, setNextPreview] = useState(lesson.nextPreview ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      updateLesson(lesson.lessonId, {
        week: Number(week),
        title,
        videoUrl,
        content,
        keyPoints,
        nextPreview,
      }),
    onSuccess: async (updated) => {
      setError(null);
      setSaved(true);
      await onDone(updated);
    },
    onError: (e) => {
      setSaved(false);
      setError(errorMessage(e, "저장하지 못했습니다."));
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    mutation.mutate();
  }

  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">수업 내용</h3>
      <form onSubmit={handleSubmit} className="mt-3 space-y-3">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700">주차</span>
          <select
            value={week}
            onChange={(e) => setWeek(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base"
          >
            {[1, 2, 3, 4, 5].map((w) => (
              <option key={w} value={w}>
                {w}주차
              </option>
            ))}
          </select>
        </label>
        <TextField label="제목" value={title} onChange={(e) => setTitle(e.target.value)} />
        <TextField
          label="영상 링크"
          placeholder="https://youtu.be/..."
          hint="YouTube 미등록 링크만 저장합니다. 파일 업로드는 없습니다."
          value={videoUrl}
          onChange={(e) => setVideoUrl(e.target.value)}
        />
        {lesson.embedUrl && (
          <div className="aspect-video w-full overflow-hidden rounded-lg bg-slate-100">
            <iframe
              src={lesson.embedUrl}
              title="수업 영상 미리보기"
              className="h-full w-full"
              allowFullScreen
            />
          </div>
        )}
        <TextAreaField
          label="수업 내용"
          hint="학생에게만 보입니다. 학부모 화면에는 나오지 않습니다."
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
        <TextAreaField
          label="중점 사항"
          rows={3}
          value={keyPoints}
          onChange={(e) => setKeyPoints(e.target.value)}
        />
        <TextAreaField
          label="다음 수업 예고"
          rows={2}
          value={nextPreview}
          onChange={(e) => setNextPreview(e.target.value)}
        />
        <FormError message={error} />
        {saved && <p className="text-sm text-emerald-700">저장했습니다.</p>}
        <SubmitButton pending={mutation.isPending}>저장</SubmitButton>
      </form>
    </section>
  );
}

/** 저장은 공개가 아니다. 작성 중인 초안이 학생에게 보이면 안 되므로 공개는 따로 누른다. */
function PublishSection({ lesson, onDone }: Props) {
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => publishLesson(lesson.lessonId),
    onSuccess: async (updated) => {
      setError(null);
      await onDone(updated);
    },
    onError: (e) => setError(errorMessage(e, "공개하지 못했습니다.")),
  });

  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">공개</h3>
      {lesson.publishedAt ? (
        <p className="mt-2 text-sm text-slate-600">
          {lesson.publishedAt.slice(0, 16).replace("T", " ")}에 공개했습니다. 이후 내용을 고쳐도
          공개 상태는 그대로입니다.
        </p>
      ) : (
        <>
          <p className="mt-2 text-xs text-slate-500">
            공개하기 전에는 학생·학부모 화면에 나오지 않습니다.
          </p>
          <button
            type="button"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
            className="mt-2 w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium
                       text-white disabled:bg-slate-300"
          >
            학생에게 공개
          </button>
        </>
      )}
      <FormError message={error} />
    </section>
  );
}

function DeleteSection({ lesson }: { lesson: LessonDetail }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => deleteLesson(lesson.lessonId),
    onSuccess: async () => {
      setConfirm(false);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
      navigate("/teacher/lessons");
    },
    onError: (e) => {
      setConfirm(false);
      setError(errorMessage(e, "삭제하지 못했습니다."));
    },
  });

  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <button
        type="button"
        onClick={() => setConfirm(true)}
        className="text-sm font-medium text-red-600 underline"
      >
        이 수업일 삭제
      </button>
      <p className="mt-1 text-xs text-slate-500">
        출석·숙제·시청 기록이 있으면 삭제되지 않습니다.
      </p>
      <FormError message={error} />

      {confirm && (
        <Modal
          title="정말 삭제할까요?"
          onClose={() => setConfirm(false)}
          footer={
            <>
              <button
                type="button"
                onClick={() => setConfirm(false)}
                className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                           font-medium text-slate-700"
              >
                취소
              </button>
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate()}
                className="flex-1 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-medium
                           text-white disabled:opacity-40"
              >
                삭제
              </button>
            </>
          }
        >
          <p className="text-sm text-slate-600">
            {lesson.classRoomName} · {lesson.lessonDate}
          </p>
        </Modal>
      )}
    </section>
  );
}

/**
 * 시청 현황. <b>미시청 학생도 나온다</b> — 선생님이 확인하려는 건 안 본 학생이다.
 *
 * <p>명단은 수업일 기준 재원생이다. 그 뒤 입반한 학생이 "안 봤다"로 잡히지 않는다.
 */
function ViewSection({ lessonId }: { lessonId: number }) {
  const { data, isPending } = useQuery({
    queryKey: ["teacher", "lesson-views", lessonId],
    queryFn: () => getLessonViews(lessonId),
  });

  if (isPending || !data) return null;

  return (
    <section className="space-y-2 rounded-xl bg-white p-4 shadow-sm">
      <div className="flex items-baseline justify-between">
        <h3 className="text-sm font-semibold text-slate-900">영상 시청 현황</h3>
        <span className="text-xs text-slate-500">
          {data.viewedCount} / {data.totalStudents}명 시청
        </span>
      </div>

      <ul className="divide-y divide-slate-100 text-sm">
        {data.items.map((item) => (
          <li key={item.studentId} className="flex items-center justify-between py-1.5">
            <span className={item.viewed ? "text-slate-900" : "text-amber-700"}>
              {item.name}
            </span>
            <span className="text-xs text-slate-500">
              {item.viewed
                ? `${Math.round(item.watchSeconds / 60)}분 · ${item.firstViewedAt
                    ?.slice(5, 16)
                    .replace("T", " ")}`
                : "미시청"}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
