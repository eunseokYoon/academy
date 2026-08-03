import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { CopyButton } from "../../../shared/components/CopyButton";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import { createClassRoom, listClassRooms } from "../api";
import type { ClassRoomSchedule } from "../api";
import { formatSchedules } from "../format";
import { ScheduleEditor, validateSchedules } from "./ScheduleEditor";

/**
 * T-3 목록. 가입 코드와 열림 상태를 항상 보이게 둔다 —
 * 등록 기간이 끝났는데 코드를 열어두는 것이 가장 흔한 사고 경로다.
 */
export default function ClassRoomListPage() {
  const [creating, setCreating] = useState(false);
  const { data, isPending } = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const openCount = (data ?? []).filter((room) => room.joinCodeActive).length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">반 관리</h2>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          반 만들기
        </button>
      </div>

      {openCount > 0 && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          가입 코드가 열린 반이 {openCount}개입니다. 등록 기간이 끝났으면 닫으세요. 열려 있는 동안은
          코드를 아는 누구나 그 반 학생으로 가입합니다.
        </p>
      )}

      {isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <ul className="space-y-2">
          {(data ?? []).map((room) => (
            <li key={room.classRoomId} className="rounded-xl bg-white p-3 shadow-sm">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <Link
                    to={`/teacher/class-rooms/${room.classRoomId}`}
                    className="font-medium text-slate-900 underline"
                  >
                    {room.name}
                  </Link>
                  <p className="mt-0.5 text-sm text-slate-500">
                    {formatSchedules(room.schedules)} · 재원 {room.studentCount}명
                  </p>
                </div>
                {room.status === "CLOSED" && <Badge tone="neutral">종료</Badge>}
              </div>
              <div className="mt-2 flex items-center gap-2">
                <span className="font-mono text-base tracking-widest text-slate-900">
                  {room.joinCode}
                </span>
                {room.joinCodeActive ? (
                  <Badge tone="warn">가입 열림</Badge>
                ) : (
                  <Badge tone="ok">가입 닫힘</Badge>
                )}
                <span className="flex-1" />
                <CopyButton value={room.joinCode} />
              </div>
            </li>
          ))}
        </ul>
      )}

      {data?.length === 0 && (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          아직 반이 없습니다. 반을 만들면 가입 코드가 발급됩니다.
        </p>
      )}

      {creating && <CreateModal onClose={() => setCreating(false)} />}
    </div>
  );
}

function CreateModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [schedules, setSchedules] = useState<ClassRoomSchedule[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ name: string; joinCode: string } | null>(null);

  const mutation = useMutation({
    mutationFn: () => createClassRoom({ name: name.trim(), schedules, memo: null }),
    onSuccess: async (result) => {
      setError(null);
      setCreated(result);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "class-rooms"] });
    },
    onError: (e) => setError(errorMessage(e, "반을 만들지 못했습니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    // 서버도 400으로 막지만, 여기서 걸러야 어디가 틀렸는지 문구로 알려줄 수 있다
    const scheduleError = validateSchedules(schedules);
    if (scheduleError) {
      setError(scheduleError);
      return;
    }
    mutation.mutate();
  }

  if (created) {
    return (
      <Modal
        title="반 가입 코드"
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
          {created.name} 학생들에게 수업에서 알려주세요. 등록이 끝나면 코드를 닫아야 합니다.
        </p>
        <div className="mt-3 flex items-center gap-2 rounded-lg bg-slate-50 p-3">
          <span className="flex-1 font-mono text-2xl tracking-widest text-slate-900">
            {created.joinCode}
          </span>
          <CopyButton value={created.joinCode} />
        </div>
      </Modal>
    );
  }

  return (
    <Modal title="반 만들기" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <TextField
          label="반 이름"
          hint="학교·학년 입력란은 없습니다. 「A고 2학년 목요일반」처럼 이름에 넣으세요."
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <ScheduleEditor value={schedules} onChange={setSchedules} />
        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>만들기</SubmitButton>
      </form>
    </Modal>
  );
}
