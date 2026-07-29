import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import { bulkCreateLessons, createLesson, listClassRooms, listLessons } from "../api";
import { DAY_LABELS, today } from "../format";

const NOW = new Date();

/**
 * T-4 목록. 주차를 먼저 고르고 반을 고른다.
 *
 * <p>선생님이 "무엇을 안 했는지" 찾는 화면이라 미완료 항목이 눈에 띄어야 한다 —
 * 내용 미작성, 미공개, 출석 미확정 셋을 배지로 보여준다.
 */
export default function LessonListPage() {
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState<number | "">("");
  const [creating, setCreating] = useState<"single" | "bulk" | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const params = {
    classRoomId: classRoomId === "" ? undefined : classRoomId,
    year,
    month,
    week: week === "" ? undefined : week,
  };
  const lessons = useQuery({
    queryKey: ["teacher", "lessons", params],
    queryFn: () => listLessons(params),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">주차별 수업 관리</h2>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setCreating("bulk")}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium
                       text-slate-700"
          >
            일괄 생성
          </button>
          <button
            type="button"
            onClick={() => setCreating("single")}
            className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
          >
            수업일 추가
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 rounded-xl bg-white p-3 text-sm shadow-sm sm:grid-cols-4">
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => setMonth(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {m}월
            </option>
          ))}
        </select>
        <select
          value={week}
          onChange={(e) => setWeek(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          <option value="">전체 주차</option>
          {[1, 2, 3, 4, 5].map((w) => (
            <option key={w} value={w}>
              {w}주차
            </option>
          ))}
        </select>
        <select
          value={classRoomId}
          onChange={(e) => setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          <option value="">전체 반</option>
          {(classRooms.data ?? []).map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>
      </div>

      {lessons.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : lessons.data?.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          이 기간에 만들어진 수업일이 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {(lessons.data ?? []).map((lesson) => (
            <li key={lesson.lessonId}>
              <Link
                to={`/teacher/lessons/${lesson.lessonId}`}
                className="block rounded-xl bg-white p-3 shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">
                    {lesson.lessonDate} ({DAY_LABELS[new Date(lesson.lessonDate).getDay() || 7]})
                  </span>
                  <span className="text-xs text-slate-400">{lesson.week}주차</span>
                </div>
                <p className="mt-0.5 text-sm text-slate-500">
                  {lesson.classRoomName}
                  {lesson.title && ` · ${lesson.title}`}
                </p>
                <div className="mt-2 flex flex-wrap gap-1">
                  {!lesson.contentWritten && <Badge tone="warn">내용 미작성</Badge>}
                  {!lesson.published && <Badge tone="warn">미공개</Badge>}
                  {lesson.attendanceStatus === "PENDING" && <Badge tone="neutral">출석 미확정</Badge>}
                  {lesson.contentWritten && lesson.published && <Badge tone="ok">공개됨</Badge>}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {creating === "single" && <CreateLessonModal onClose={() => setCreating(null)} />}
      {creating === "bulk" && <BulkCreateModal onClose={() => setCreating(null)} />}
    </div>
  );
}

/** 주차는 선생님이 고른 값을 그대로 저장한다. 서버가 날짜에서 계산해 덮어쓰지 않는다. */
function CreateLessonModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [classRoomId, setClassRoomId] = useState("");
  const [lessonDate, setLessonDate] = useState(today());
  const [week, setWeek] = useState("1");
  const [title, setTitle] = useState("");
  const [error, setError] = useState<string | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms", "ACTIVE"],
    queryFn: () => listClassRooms("ACTIVE"),
  });

  const mutation = useMutation({
    mutationFn: () => {
      const date = new Date(lessonDate);
      return createLesson({
        classRoomId: Number(classRoomId),
        lessonDate,
        year: date.getFullYear(),
        month: date.getMonth() + 1,
        week: Number(week),
        title: title.trim() || null,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e, "수업일을 만들지 못했습니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <Modal title="수업일 추가" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700">반</span>
          <select
            value={classRoomId}
            onChange={(e) => setClassRoomId(e.target.value)}
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
        <TextField
          label="수업일"
          type="date"
          value={lessonDate}
          onChange={(e) => setLessonDate(e.target.value)}
          required
        />
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
          <span className="mt-1 block text-xs text-slate-500">
            달 경계에 걸친 주는 세는 방식이 갈립니다. 자료·성적과 같은 번호를 쓰세요.
          </span>
        </label>
        <TextField label="제목" value={title} onChange={(e) => setTitle(e.target.value)} />
        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>만들기</SubmitButton>
      </form>
    </Modal>
  );
}

/** 한 학기 20회를 매번 손으로 만들게 하면 안 된다. 반의 요일 기준으로 한 번에 만든다. */
function BulkCreateModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [classRoomId, setClassRoomId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [skipDates, setSkipDates] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ created: number; skipped: number } | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms", "ACTIVE"],
    queryFn: () => listClassRooms("ACTIVE"),
  });
  const selected = (classRooms.data ?? []).find(
    (room) => room.classRoomId === Number(classRoomId),
  );

  const mutation = useMutation({
    mutationFn: () =>
      bulkCreateLessons({
        classRoomId: Number(classRoomId),
        from,
        to,
        skipDates: skipDates
          .split(",")
          .map((date) => date.trim())
          .filter(Boolean),
      }),
    onSuccess: async (created) => {
      setError(null);
      setResult(created);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
    },
    onError: (e) => setError(errorMessage(e, "일괄 생성하지 못했습니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  if (result) {
    return (
      <Modal
        title="일괄 생성 완료"
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
          {result.created}일을 만들었습니다. 건너뛴 날짜는 {result.skipped}일입니다.
        </p>
        <p className="mt-2 text-xs text-slate-500">
          주차는 달력 기준으로 채워졌습니다. 다르면 각 수업 화면에서 고치세요.
        </p>
      </Modal>
    );
  }

  return (
    <Modal title="수업일 일괄 생성" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700">반</span>
          <select
            value={classRoomId}
            onChange={(e) => setClassRoomId(e.target.value)}
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
          {selected && (
            <span className="mt-1 block text-xs text-slate-500">
              {selected.dayOfWeek
                ? `${DAY_LABELS[selected.dayOfWeek]}요일마다 만듭니다.`
                : "이 반에는 요일이 없어 일괄 생성을 쓸 수 없습니다. 반 정보에서 요일을 지정하세요."}
            </span>
          )}
        </label>
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="시작일"
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            required
          />
          <TextField
            label="종료일"
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            required
          />
        </div>
        <TextField
          label="제외할 날짜"
          placeholder="2026-05-05, 2026-06-06"
          hint="공휴일·휴강일을 쉼표로 구분해 적으세요."
          value={skipDates}
          onChange={(e) => setSkipDates(e.target.value)}
        />
        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>만들기</SubmitButton>
      </form>
    </Modal>
  );
}
