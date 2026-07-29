import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextAreaField } from "../../../shared/components/TextAreaField";
import { TextField } from "../../../shared/components/TextField";
import { SUBMISSION_LABELS, formatDueAt } from "../../../shared/homework/types";
import type { SubmissionStatus } from "../../../shared/homework/types";
import { deleteHomework, getHomework, listSubmissions, updateHomework } from "../api";
import type { SubmissionListItem } from "../api";
import SubmissionViewer from "./SubmissionViewer";

const CARD_TONE: Record<SubmissionStatus, string> = {
  SUBMITTED: "border-amber-300",
  NOT_SUBMITTED: "border-slate-200 bg-slate-100",
  CHECKED: "border-emerald-200",
};

/**
 * T-7 숙제 확인 격자. <b>강사 1명이 200명을 확인하는 화면이다.</b>
 *
 * <p>정렬은 서버가 처리할 것 우선으로 준다: 확인 대기 → 미제출 → 확인 완료.
 * 미제출은 회색 카드라 한눈에 구분된다.
 */
export default function HomeworkDetailPage() {
  const { homeworkId } = useParams();
  const id = Number(homeworkId);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [viewing, setViewing] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [editing, setEditing] = useState(false);

  const submissions = useQuery({
    queryKey: ["teacher", "submissions", id],
    queryFn: () => listSubmissions(id),
  });

  if (submissions.isPending) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }
  if (!submissions.data) {
    return <p className="text-sm text-slate-500">숙제를 찾을 수 없습니다.</p>;
  }

  const { homework, counts, items } = submissions.data;

  return (
    <div className="space-y-4">
      <div>
        <button
          type="button"
          onClick={() => navigate("/teacher/homeworks")}
          className="text-sm text-slate-500"
        >
          ← 숙제 목록
        </button>
        <div className="mt-2 flex items-start justify-between gap-2">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{homework.title}</h2>
            <p className="text-sm text-slate-500">
              {homework.classRoomName} · {formatDueAt(homework.dueAt)} 마감
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-600"
            >
              수정
            </button>
            <button
              type="button"
              onClick={() => setDeleting(true)}
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-600"
            >
              삭제
            </button>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap gap-1">
        <Badge tone="warn">확인 대기 {counts.submitted}</Badge>
        <Badge tone="neutral">미제출 {counts.notSubmitted}</Badge>
        <Badge tone="ok">완료 {counts.checked}</Badge>
      </div>

      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {items.map((item) => (
          <SubmissionCard
            key={item.submissionId}
            item={item}
            onOpen={() => setViewing(item.submissionId)}
          />
        ))}
      </ul>

      {viewing !== null && (
        <SubmissionViewer
          submissionId={viewing}
          onNavigate={setViewing}
          onClose={() => setViewing(null)}
        />
      )}

      {editing && <EditHomeworkModal homeworkId={id} onClose={() => setEditing(false)} />}

      {deleting && (
        <DeleteHomeworkModal
          homeworkId={id}
          hasSubmissions={counts.submitted + counts.checked > 0}
          onClose={() => setDeleting(false)}
          onDeleted={async () => {
            await queryClient.invalidateQueries({ queryKey: ["teacher", "homeworks"] });
            navigate("/teacher/homeworks");
          }}
        />
      )}
    </div>
  );
}

function SubmissionCard({ item, onOpen }: { item: SubmissionListItem; onOpen: () => void }) {
  const notSubmitted = item.status === "NOT_SUBMITTED";

  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        disabled={notSubmitted}
        className={`w-full overflow-hidden rounded-xl border-2 bg-white text-left shadow-sm
                    ${CARD_TONE[item.status]} disabled:cursor-default`}
      >
        <div className="relative flex aspect-square items-center justify-center bg-slate-100">
          {item.thumbnailUrl ? (
            <img
              src={item.thumbnailUrl}
              alt={`${item.studentName} 제출 사진`}
              className="h-full w-full object-cover"
              loading="lazy"
            />
          ) : item.hasVideo ? (
            // 영상만 낸 제출물. 트랜스코딩을 안 해서 썸네일을 만들 수 없다
            <span className="text-2xl text-slate-400" aria-label="영상 제출">
              ▶
            </span>
          ) : (
            <span className="text-xs text-slate-400">미제출</span>
          )}
          {item.hasVideo && item.thumbnailUrl && (
            <span
              className="absolute bottom-1 right-1 rounded bg-slate-900/70 px-1.5 py-0.5
                         text-[10px] text-white"
            >
              영상
            </span>
          )}
        </div>
        <div className="p-2">
          <p className="truncate text-sm font-medium text-slate-900">{item.studentName}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {SUBMISSION_LABELS[item.status]}
            {item.isLate && " · 늦음"}
            {item.photoCount > 0 && ` · ${item.photoCount}장`}
          </p>
        </div>
      </button>
    </li>
  );
}

/**
 * 마감은 <b>늦추는 방향만</b> 된다. 앞당기면 이미 제출한 학생의 지각 여부를
 * 전부 다시 계산해야 해서 서버가 막는다. 앞당기려면 삭제 후 재출제한다.
 */
function EditHomeworkModal({
  homeworkId,
  onClose,
}: {
  homeworkId: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const homework = useQuery({
    queryKey: ["teacher", "homework", homeworkId],
    queryFn: () => getHomework(homeworkId),
  });

  const [form, setForm] = useState<{ title: string; description: string; dueAt: string } | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);

  // 서버 값이 오면 한 번만 폼에 싣는다
  const data = homework.data;
  if (data && form === null) {
    setForm({
      title: data.title,
      description: data.description ?? "",
      // datetime-local은 오프셋을 못 받는다. 앞 16자만 쓰면 KST 그대로다
      dueAt: data.dueAt.slice(0, 16),
    });
  }

  const mutation = useMutation({
    mutationFn: () =>
      updateHomework(homeworkId, {
        title: form!.title.trim(),
        description: form!.description.trim() || null,
        lessonId: data?.lessonId ?? null,
        dueAt: `${form!.dueAt}:00+09:00`,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "homeworks"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "submissions", homeworkId] });
      onClose();
    },
    onError: (e) =>
      setError(errorMessage(e, "수정하지 못했습니다. 마감은 늦추는 방향만 됩니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <Modal title="숙제 수정" onClose={onClose}>
      {form === null ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-3">
          <TextField
            label="제목"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />
          <TextAreaField
            label="내용"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={3}
          />
          <TextField
            label="마감"
            type="datetime-local"
            value={form.dueAt}
            onChange={(e) => setForm({ ...form, dueAt: e.target.value })}
            hint="늦추는 방향만 됩니다. 앞당기려면 삭제 후 다시 출제하세요."
            required
          />
          <FormError message={error} />
          <SubmitButton pending={mutation.isPending}>저장</SubmitButton>
        </form>
      )}
    </Modal>
  );
}

/**
 * 삭제는 전원 미제출일 때만 된다. 사진을 올린 학생이 있으면 서버가 409를 준다 —
 * 학생이 올린 사진이 경고 없이 사라지면 안 된다.
 */
function DeleteHomeworkModal({
  homeworkId,
  hasSubmissions,
  onClose,
  onDeleted,
}: {
  homeworkId: number;
  hasSubmissions: boolean;
  onClose: () => void;
  onDeleted: () => Promise<void>;
}) {
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () => deleteHomework(homeworkId),
    onSuccess: onDeleted,
    onError: (e) => setError(errorMessage(e, "삭제하지 못했습니다.")),
  });

  return (
    <Modal
      title="숙제 삭제"
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm text-slate-700"
          >
            취소
          </button>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || hasSubmissions}
            className="flex-1 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-medium text-white
                       disabled:bg-slate-300"
          >
            삭제
          </button>
        </>
      }
    >
      {hasSubmissions ? (
        <p className="text-sm text-slate-600">
          이미 제출한 학생이 있어 삭제할 수 없습니다. 학생이 올린 사진이 함께 사라지기 때문입니다.
          마감을 늦추거나 그대로 두세요.
        </p>
      ) : (
        <p className="text-sm text-slate-600">
          아직 아무도 제출하지 않았습니다. 삭제하면 되돌릴 수 없습니다.
        </p>
      )}
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
    </Modal>
  );
}
