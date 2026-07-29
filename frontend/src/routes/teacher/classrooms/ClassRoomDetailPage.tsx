import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { CopyButton } from "../../../shared/components/CopyButton";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import {
  assignStudents,
  changeJoinCode,
  closeClassRoom,
  deleteClassRoom,
  getClassRoom,
  listClassRoomStudents,
  listStudents,
  unassignStudent,
  updateClassRoom,
} from "../api";
import type { ClassRoom } from "../api";
import { DAY_LABELS, today } from "../format";

export default function ClassRoomDetailPage() {
  const classRoomId = Number(useParams().classRoomId);
  const queryClient = useQueryClient();

  const { data, isPending } = useQuery({
    queryKey: ["teacher", "class-rooms", classRoomId],
    queryFn: () => getClassRoom(classRoomId),
  });

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["teacher", "class-rooms"] });

  if (isPending || !data) return <p className="text-sm text-slate-400">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold text-slate-900">{data.name}</h2>
        {data.status === "CLOSED" && <Badge tone="neutral">종료</Badge>}
      </div>

      <JoinCodeSection classRoom={data} onDone={refresh} />
      <MemberSection classRoomId={classRoomId} />
      <ProfileSection classRoom={data} onDone={refresh} />
      <DangerSection classRoom={data} onDone={refresh} />

      <Link to="/teacher/class-rooms" className="block py-2 text-sm text-slate-500 underline">
        반 목록으로
      </Link>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <div className="mt-3">{children}</div>
    </section>
  );
}

interface SectionProps {
  classRoom: ClassRoom;
  onDone: () => Promise<unknown>;
}

/** 코드는 반 전체가 나눠 쓰는 값이라 전화번호 대조가 없다. 닫는 것이 유일한 방어선이다. */
function JoinCodeSection({ classRoom, onDone }: SectionProps) {
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (body: { regenerate?: boolean; active?: boolean }) =>
      changeJoinCode(classRoom.classRoomId, body),
    onSuccess: async () => {
      setError(null);
      await onDone();
    },
    onError: (e) => setError(errorMessage(e, "코드를 바꾸지 못했습니다.")),
  });

  return (
    <Section title="가입 코드">
      <div className="flex items-center gap-2">
        <span className="flex-1 font-mono text-2xl tracking-widest text-slate-900">
          {classRoom.joinCode}
        </span>
        <CopyButton value={classRoom.joinCode} />
      </div>
      <div className="mt-2">
        {classRoom.joinCodeActive ? (
          <Badge tone="warn">가입 열림</Badge>
        ) : (
          <Badge tone="ok">가입 닫힘</Badge>
        )}
      </div>
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate({ active: !classRoom.joinCodeActive })}
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium
                     text-slate-700"
        >
          {classRoom.joinCodeActive ? "가입 닫기" : "가입 열기"}
        </button>
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate({ regenerate: true, active: true })}
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium
                     text-slate-700"
        >
          코드 재발급
        </button>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        재발급하면 이전 코드는 즉시 무효입니다. 이미 가입한 학생은 그대로 남습니다.
      </p>
      <FormError message={error} />
    </Section>
  );
}

/** 명단은 재원생만 보여준다. 미가입 학생도 이름과 함께 나와야 한다. */
function MemberSection({ classRoomId }: { classRoomId: number }) {
  const queryClient = useQueryClient();
  const [asOf, setAsOf] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data } = useQuery({
    queryKey: ["teacher", "class-rooms", classRoomId, "students", asOf],
    queryFn: () => listClassRoomStudents(classRoomId, asOf || undefined),
  });

  const remove = useMutation({
    mutationFn: (studentId: number) => unassignStudent(classRoomId, studentId),
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "class-rooms"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "students"] });
    },
    onError: (e) => setError(errorMessage(e, "배정을 해제하지 못했습니다.")),
  });

  return (
    <Section title={`명단 (${data?.students.length ?? 0}명)`}>
      <div className="flex items-center gap-2">
        <label className="flex-1 text-xs text-slate-500">
          기준일
          <input
            type="date"
            value={asOf}
            onChange={(e) => setAsOf(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
          />
        </label>
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-4 shrink-0 rounded-lg border border-slate-300 px-3 py-2 text-sm
                     font-medium text-slate-700"
        >
          학생 배정
        </button>
      </div>

      <ul className="mt-3 divide-y divide-slate-100">
        {(data?.students ?? []).map((member) => (
          <li key={member.studentId} className="flex items-center gap-2 py-2">
            <Link
              to={`/teacher/students/${member.studentId}`}
              className="text-sm text-slate-800 underline"
            >
              {member.name}
            </Link>
            {!member.signedUp && <Badge tone="warn">미가입</Badge>}
            <span className="flex-1" />
            <span className="text-xs text-slate-400">{member.joinedAt}</span>
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate(member.studentId)}
              className="text-xs text-slate-500 underline"
            >
              해제
            </button>
          </li>
        ))}
      </ul>
      {data?.students.length === 0 && (
        <p className="py-3 text-sm text-slate-500">이 반에 배정된 학생이 없습니다.</p>
      )}
      <FormError message={error} />

      {adding && <AssignModal classRoomId={classRoomId} onClose={() => setAdding(false)} />}
    </Section>
  );
}

function AssignModal({ classRoomId, onClose }: { classRoomId: number; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);

  const candidates = useQuery({
    queryKey: ["teacher", "students", { keyword: search, status: "ENROLLED", size: 30 }],
    queryFn: () =>
      listStudents({ keyword: search || undefined, status: "ENROLLED", page: 0, size: 30 }),
  });

  const mutation = useMutation({
    mutationFn: () => assignStudents(classRoomId, selected, today()),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "class-rooms"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "students"] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e, "배정하지 못했습니다.")),
  });

  return (
    <Modal
      title="학생 배정"
      onClose={onClose}
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                       font-medium text-slate-700"
          >
            취소
          </button>
          <button
            type="button"
            disabled={selected.length === 0 || mutation.isPending}
            onClick={() => mutation.mutate()}
            className="flex-1 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white
                       disabled:bg-slate-300"
          >
            {selected.length}명 배정
          </button>
        </>
      }
    >
      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(keyword.trim());
        }}
      >
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="이름 또는 번호"
          className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-base"
        />
        <button
          type="submit"
          className="shrink-0 rounded-lg border border-slate-300 px-3 py-2 text-sm
                     font-medium text-slate-700"
        >
          검색
        </button>
      </form>

      {/* 이미 배정된 학생을 골라도 서버가 멱등하게 무시한다 */}
      <ul className="mt-3 max-h-64 space-y-1 overflow-y-auto">
        {(candidates.data?.items ?? []).map((student) => (
          <li key={student.studentId}>
            <label className="flex items-center gap-2 py-1 text-sm">
              <input
                type="checkbox"
                checked={selected.includes(student.studentId)}
                onChange={(e) =>
                  setSelected((prev) =>
                    e.target.checked
                      ? [...prev, student.studentId]
                      : prev.filter((id) => id !== student.studentId),
                  )
                }
                className="h-4 w-4 rounded border-slate-300"
              />
              <span className="text-slate-800">{student.name}</span>
              <span className="text-xs text-slate-400">
                {student.classRooms.join(" · ") || "반 미배정"}
              </span>
            </label>
          </li>
        ))}
      </ul>
      <FormError message={error} />
    </Modal>
  );
}

function ProfileSection({ classRoom, onDone }: SectionProps) {
  const [name, setName] = useState(classRoom.name);
  const [dayOfWeek, setDayOfWeek] = useState(classRoom.dayOfWeek?.toString() ?? "");
  const [startTime, setStartTime] = useState(classRoom.startTime?.slice(0, 5) ?? "");
  const [termStart, setTermStart] = useState(classRoom.termStart ?? "");
  const [termEnd, setTermEnd] = useState(classRoom.termEnd ?? "");
  const [memo, setMemo] = useState(classRoom.memo ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      updateClassRoom(classRoom.classRoomId, {
        name: name.trim(),
        dayOfWeek: dayOfWeek ? Number(dayOfWeek) : undefined,
        startTime: startTime || undefined,
        termStart: termStart || undefined,
        termEnd: termEnd || undefined,
        memo,
      }),
    onSuccess: async () => {
      setError(null);
      setSaved(true);
      await onDone();
    },
    onError: (e) => {
      setSaved(false);
      setError(errorMessage(e, "수정하지 못했습니다."));
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    mutation.mutate();
  }

  return (
    <Section title="반 정보">
      <form onSubmit={handleSubmit} className="space-y-3">
        <TextField label="반 이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <div className="grid grid-cols-2 gap-2">
          <label className="block">
            <span className="block text-sm font-medium text-slate-700">요일</span>
            <select
              value={dayOfWeek}
              onChange={(e) => setDayOfWeek(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5
                         text-base"
            >
              <option value="">미정</option>
              {[1, 2, 3, 4, 5, 6, 7].map((day) => (
                <option key={day} value={day}>
                  {DAY_LABELS[day]}
                </option>
              ))}
            </select>
          </label>
          <TextField
            label="시작 시각"
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="시작일"
            type="date"
            value={termStart}
            onChange={(e) => setTermStart(e.target.value)}
          />
          <TextField
            label="종료일"
            type="date"
            value={termEnd}
            onChange={(e) => setTermEnd(e.target.value)}
          />
        </div>
        <TextField label="메모" value={memo} onChange={(e) => setMemo(e.target.value)} />
        <p className="text-xs text-slate-500">
          이름을 바꾸면 지난 수업의 반 이름 표기도 함께 바뀝니다.
        </p>
        <FormError message={error} />
        {saved && <p className="text-sm text-emerald-700">저장했습니다.</p>}
        <SubmitButton pending={mutation.isPending}>저장</SubmitButton>
      </form>
    </Section>
  );
}

/** 학기가 끝난 반은 종료, 잘못 만든 반은 삭제다. 운영이 시작된 반은 삭제되지 않는다. */
function DangerSection({ classRoom, onDone }: SectionProps) {
  const navigate = useNavigate();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = useMutation({
    mutationFn: () => closeClassRoom(classRoom.classRoomId),
    onSuccess: async () => {
      setError(null);
      await onDone();
    },
    onError: (e) => setError(errorMessage(e, "종료 처리하지 못했습니다.")),
  });

  const remove = useMutation({
    mutationFn: () => deleteClassRoom(classRoom.classRoomId),
    onSuccess: async () => {
      setConfirmDelete(false);
      await onDone();
      navigate("/teacher/class-rooms");
    },
    onError: (e) => {
      setConfirmDelete(false);
      setError(errorMessage(e, "삭제하지 못했습니다."));
    },
  });

  return (
    <Section title="종료 · 삭제">
      {classRoom.status === "ACTIVE" && (
        <>
          <button
            type="button"
            disabled={close.isPending}
            onClick={() => close.mutate()}
            className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                       font-medium text-slate-700"
          >
            학기 종료 처리
          </button>
          <p className="mt-1 text-xs text-slate-500">
            수업·출석 기록은 남고 가입 코드가 닫힙니다. 같은 이름의 반을 다시 만들 수 있습니다.
          </p>
        </>
      )}

      <div className="mt-4 border-t border-slate-100 pt-4">
        <button
          type="button"
          onClick={() => setConfirmDelete(true)}
          className="text-sm font-medium text-red-600 underline"
        >
          이 반 삭제
        </button>
        <p className="mt-1 text-xs text-slate-500">
          이름을 잘못 입력해 방금 만든 반을 지우는 기능입니다. 수업·배정 이력이 있으면 삭제되지
          않습니다.
        </p>
      </div>

      <FormError message={error} />

      {confirmDelete && (
        <Modal
          title="정말 삭제할까요?"
          onClose={() => setConfirmDelete(false)}
          footer={
            <>
              <button
                type="button"
                onClick={() => setConfirmDelete(false)}
                className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                           font-medium text-slate-700"
              >
                취소
              </button>
              <button
                type="button"
                disabled={remove.isPending}
                onClick={() => remove.mutate()}
                className="flex-1 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-medium
                           text-white disabled:opacity-40"
              >
                삭제
              </button>
            </>
          }
        >
          <p className="text-sm text-slate-600">
            {classRoom.name} · 재원 {classRoom.studentCount}명
          </p>
          <p className="mt-2 text-sm text-red-700">
            수업이나 배정 이력이 하나라도 있으면 삭제되지 않고 종료 처리를 안내합니다.
          </p>
        </Modal>
      )}
    </Section>
  );
}
