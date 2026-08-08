import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextAreaField } from "../../../shared/components/TextAreaField";
import { TextField } from "../../../shared/components/TextField";
import { formatDueAt } from "../../../shared/homework/types";
import {
  createHomework,
  listClassRooms,
  listHomeworkTemplates,
  listHomeworks,
  listLessons,
} from "../api";
import type { HomeworkTemplate } from "../api";

/**
 * T-6 숙제 출제·목록.
 *
 * <p>출제하면 대상 전원의 제출물 행이 그 자리에서 만들어진다. 그래서 목록의 미제출 수는
 * 출제 직후부터 정확하다 — 나중에 역산하지 않는다.
 */
export default function HomeworkListPage() {
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [creating, setCreating] = useState(false);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const params = { classRoomId: classRoomId === "" ? undefined : classRoomId };
  const homeworks = useQuery({
    queryKey: ["teacher", "homeworks", params],
    queryFn: () => listHomeworks(params),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">숙제</h2>
        <div className="flex gap-2">
          {/* 반 × 수업일 채점 그리드(T-6b). 종이로 걷은 숙제는 여기서 채점한다 */}
          <Link
            to="/teacher/homework-grid"
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium
                       text-slate-700"
          >
            숙제 채점
          </Link>
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
          >
            숙제 출제
          </button>
        </div>
      </div>

      <select
        value={classRoomId}
        onChange={(e) => setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))}
        className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm sm:w-64"
      >
        <option value="">전체 반</option>
        {(classRooms.data ?? []).map((room) => (
          <option key={room.classRoomId} value={room.classRoomId}>
            {room.name}
          </option>
        ))}
      </select>

      {homeworks.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : homeworks.data?.items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          출제한 숙제가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {(homeworks.data?.items ?? []).map((homework) => (
            <li key={homework.homeworkId}>
              <Link
                // GRID 열은 상세가 아니라 채점 화면으로 보낸다. 그리드 화면은 반·수업일을
                // 자기 상태로 고르므로 여기서는 쿼리 파라미터 없이 화면만 열어 준다
                to={
                  homework.kind === "GRID"
                    ? "/teacher/homework-grid"
                    : `/teacher/homeworks/${homework.homeworkId}`
                }
                className="block rounded-xl bg-white p-3 shadow-sm"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex items-center gap-1.5">
                    <Badge tone={homework.kind === "GRID" ? "neutral" : "ok"}>
                      {homework.kind === "GRID" ? "그리드" : "온라인"}
                    </Badge>
                    <span className="font-medium text-slate-900">{homework.title}</span>
                  </div>
                  {/* GRID는 재제출을 열기 전까지 마감이 없다 */}
                  {homework.dueAt !== null && (
                    <span className="shrink-0 text-xs text-slate-400">
                      {formatDueAt(homework.dueAt)} 마감
                    </span>
                  )}
                </div>
                <p className="mt-0.5 text-sm text-slate-500">{homework.classRoomName}</p>
                <div className="mt-2 flex flex-wrap items-center gap-1">
                  {homework.counts.submitted > 0 && (
                    <Badge tone="warn">확인 대기 {homework.counts.submitted}</Badge>
                  )}
                  {homework.counts.notSubmitted > 0 && (
                    <Badge tone="neutral">미제출 {homework.counts.notSubmitted}</Badge>
                  )}
                  {homework.counts.checked > 0 && (
                    <Badge tone="ok">완료 {homework.counts.checked}</Badge>
                  )}
                  {/* 수업에 연결하지 않으면 캘린더 숙제 완료율이 비어 있다 */}
                  {homework.lessonId === null && <Badge tone="danger">수업 미연결</Badge>}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {creating && <CreateHomeworkModal onClose={() => setCreating(false)} />}
    </div>
  );
}

/** 대상은 반 전체다. 일부 학생만 고르는 기능은 없다. */
function CreateHomeworkModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [classRoomId, setClassRoomId] = useState("");
  const [lessonId, setLessonId] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [dueTime, setDueTime] = useState("20:00");
  const [templateId, setTemplateId] = useState<number | null>(null);
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ targetCount: number } | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms", "ACTIVE"],
    queryFn: () => listClassRooms("ACTIVE"),
  });
  const templates = useQuery({
    queryKey: ["teacher", "homework-templates"],
    queryFn: listHomeworkTemplates,
  });
  const lessons = useQuery({
    queryKey: ["teacher", "lessons", { classRoomId }],
    queryFn: () => listLessons({ classRoomId: Number(classRoomId) }),
    enabled: classRoomId !== "",
  });

  const mutation = useMutation({
    mutationFn: () =>
      createHomework({
        classRoomId: Number(classRoomId),
        lessonId: lessonId === "" ? null : Number(lessonId),
        title: title.trim(),
        description: description.trim() || null,
        // 서버는 마감일 기준으로 재원생을 뽑는다. 오프셋을 붙여 KST로 못 박는다
        dueAt: `${dueDate}T${dueTime}:00+09:00`,
        templateId,
        saveAsTemplate,
      }),
    onSuccess: async (created) => {
      setError(null);
      setResult(created);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "homeworks"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "homework-templates"] });
    },
    onError: (e) => setError(errorMessage(e, "숙제를 출제하지 못했습니다.")),
  });

  function applyTemplate(template: HomeworkTemplate) {
    setTemplateId(template.id);
    setTitle(template.title);
    setDescription(template.description ?? "");
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  if (result) {
    return (
      <Modal
        title="출제 완료"
        onClose={onClose}
        footer={
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white"
          >
            확인
          </button>
        }
      >
        <p className="text-sm text-slate-600">
          {result.targetCount}명에게 출제했습니다. 마감일 기준 재원생 전원입니다.
        </p>
        {result.targetCount === 0 && (
          <p className="mt-2 text-xs text-red-600">
            대상이 0명입니다. 마감일에 이 반에 다니는 학생이 없는지 확인하세요.
          </p>
        )}
      </Modal>
    );
  }

  return (
    <Modal title="숙제 출제" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        {(templates.data ?? []).length > 0 && (
          <div>
            <span className="block text-sm font-medium text-slate-700">자주 내는 숙제</span>
            <div className="mt-1 flex flex-wrap gap-1">
              {(templates.data ?? []).map((template) => (
                <button
                  key={template.id}
                  type="button"
                  onClick={() => applyTemplate(template)}
                  className={`rounded-lg border px-2 py-1 text-xs ${
                    templateId === template.id
                      ? "border-slate-900 bg-slate-900 text-white"
                      : "border-slate-300 text-slate-600"
                  }`}
                >
                  {template.title}
                </button>
              ))}
            </div>
          </div>
        )}

        <label className="block">
          <span className="block text-sm font-medium text-slate-700">반</span>
          <select
            value={classRoomId}
            onChange={(e) => {
              setClassRoomId(e.target.value);
              setLessonId("");
            }}
            required
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base"
          >
            <option value="">선택</option>
            {(classRooms.data ?? []).map((room) => (
              <option key={room.classRoomId} value={room.classRoomId}>
                {room.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="block text-sm font-medium text-slate-700">수업 연결</span>
          <select
            value={lessonId}
            onChange={(e) => setLessonId(e.target.value)}
            disabled={classRoomId === ""}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base
                       disabled:bg-slate-100"
          >
            <option value="">연결 안 함</option>
            {(lessons.data ?? []).map((lesson) => (
              <option key={lesson.lessonId} value={lesson.lessonId}>
                {lesson.lessonDate} {lesson.title ?? ""}
              </option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-slate-500">
            연결해야 학생·학부모 캘린더에 숙제 완료율이 표시됩니다.
          </span>
        </label>

        <TextField
          label="제목"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
        <TextAreaField
          label="내용"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
        />

        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="마감일"
            type="date"
            value={dueDate}
            onChange={(e) => setDueDate(e.target.value)}
            required
          />
          <TextField
            label="마감 시각"
            type="time"
            value={dueTime}
            onChange={(e) => setDueTime(e.target.value)}
            required
          />
        </div>
        <p className="text-xs text-slate-500">
          마감 후에도 제출은 받습니다. 늦게 낸 것으로 표시될 뿐입니다.
        </p>

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={saveAsTemplate}
            onChange={(e) => setSaveAsTemplate(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          자주 내는 숙제로 저장
        </label>

        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>출제하기</SubmitButton>
      </form>
    </Modal>
  );
}
