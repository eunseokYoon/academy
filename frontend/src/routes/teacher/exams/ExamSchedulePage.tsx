import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { EXAM_TYPE_LABELS } from "../../../shared/score/types";
import type { ExamType } from "../../../shared/score/types";
import {
  createExamSchedule,
  deleteExamSchedule,
  listClassRooms,
  listExamSchedules,
} from "../api";
import { today } from "../format";

const NOW = new Date();

/** 격자 열 순서. 학기 × 시험종류 4칸이다. */
const COLUMNS: { semester: number; examType: ExamType; label: string }[] = [
  { semester: 1, examType: "MIDTERM", label: "1학기 중간" },
  { semester: 1, examType: "FINAL", label: "1학기 기말" },
  { semester: 2, examType: "MIDTERM", label: "2학기 중간" },
  { semester: 2, examType: "FINAL", label: "2학기 기말" },
];

/**
 * T-11. 반 × 시험 격자로 등록 현황을 보여준다.
 *
 * <p><b>미등록 칸이 눈에 보여야 한다.</b> 등록이 빠진 반은 그 반 학생들의 D-day가
 * 통째로 비어 있게 되고, 반이 늘어날수록 빠뜨리기 쉽다.
 * 그래서 등록 화면에서 반을 다중 선택하고 한 번의 저장으로 여러 행을 만든다.
 */
export default function ExamSchedulePage() {
  const queryClient = useQueryClient();
  const [year, setYear] = useState(NOW.getFullYear());
  const [creating, setCreating] = useState<{ semester: number; examType: ExamType } | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });
  const schedules = useQuery({
    queryKey: ["teacher", "exam-schedules", year],
    queryFn: () => listExamSchedules({ year }),
  });

  const remove = useMutation({
    mutationFn: deleteExamSchedule,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["teacher", "exam-schedules"] }),
  });

  const rooms = (classRooms.data ?? []).filter((room) => room.status === "ACTIVE");
  const byCell = new Map(
    (schedules.data ?? []).map((s) => [`${s.classRoomId}-${s.semester}-${s.examType}`, s]),
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">시험 일정 관리</h2>
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
      </div>

      <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
        여기 등록된 일정이 학생·학부모 홈 D-day의 유일한 근거입니다. 빈 칸인 반은 D-day가
        표시되지 않습니다.
      </p>

      <div className="overflow-x-auto rounded-xl bg-white shadow-sm">
        <table className="w-full min-w-[560px] text-sm">
          <thead className="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th className="px-3 py-2 text-left font-medium">반</th>
              {COLUMNS.map((column) => (
                <th key={column.label} className="px-3 py-2 text-left font-medium">
                  <button
                    type="button"
                    onClick={() =>
                      setCreating({ semester: column.semester, examType: column.examType })
                    }
                    className="underline"
                  >
                    {column.label} +
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {rooms.map((room) => (
              <tr key={room.classRoomId}>
                <td className="px-3 py-2 text-slate-900">{room.name}</td>
                {COLUMNS.map((column) => {
                  const cell = byCell.get(
                    `${room.classRoomId}-${column.semester}-${column.examType}`,
                  );
                  return (
                    <td key={column.label} className="px-3 py-2">
                      {cell ? (
                        <div className="space-y-0.5">
                          <p className="text-xs text-slate-700">
                            {cell.startDate.slice(5).replace(/-/g, ".")}~
                            {cell.endDate.slice(5).replace(/-/g, ".")}
                          </p>
                          <button
                            type="button"
                            onClick={() => {
                              if (window.confirm(`${room.name} ${column.label} 일정을 삭제할까요?`)) {
                                remove.mutate(cell.examScheduleId);
                              }
                            }}
                            className="text-xs text-slate-400 underline"
                          >
                            삭제
                          </button>
                        </div>
                      ) : (
                        // 미등록이 눈에 띄어야 한다
                        <span className="text-amber-600">미등록</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {remove.isError && <FormError message={errorMessage(remove.error)} />}

      {creating && (
        <CreateModal
          year={year}
          semester={creating.semester}
          examType={creating.examType}
          classRooms={rooms.map((room) => ({ id: room.classRoomId, name: room.name }))}
          registered={new Set(
            (schedules.data ?? [])
              .filter(
                (s) => s.semester === creating.semester && s.examType === creating.examType,
              )
              .map((s) => s.classRoomId),
          )}
          onClose={() => setCreating(null)}
          onDone={() => {
            void queryClient.invalidateQueries({ queryKey: ["teacher", "exam-schedules"] });
            setCreating(null);
          }}
        />
      )}
    </div>
  );
}

/**
 * 반 다중 선택. 반마다 한 행이라 선택한 수만큼 요청을 보낸다 —
 * 학교·학년 개념이 없어 서버에서 한 번에 묶을 수 없다.
 *
 * <p>이미 등록된 반은 체크할 수 없다. 보내면 409가 나므로 화면에서 미리 막는다.
 */
function CreateModal({
  year,
  semester,
  examType,
  classRooms,
  registered,
  onClose,
  onDone,
}: {
  year: number;
  semester: number;
  examType: ExamType;
  classRooms: { id: number; name: string }[];
  registered: Set<number>;
  onClose: () => void;
  onDone: () => void;
}) {
  const [selected, setSelected] = useState<number[]>([]);
  const [startDate, setStartDate] = useState(today());
  const [endDate, setEndDate] = useState(today());
  const [scopeNote, setScopeNote] = useState("");

  const save = useMutation({
    mutationFn: async () => {
      for (const classRoomId of selected) {
        await createExamSchedule({
          classRoomId,
          year,
          semester,
          examType,
          startDate,
          endDate,
          scopeNote: scopeNote.trim() === "" ? null : scopeNote.trim(),
        });
      }
    },
    onSuccess: onDone,
  });

  const selectable = classRooms.filter((room) => !registered.has(room.id));

  function submit(event: FormEvent) {
    event.preventDefault();
    if (selected.length > 0) save.mutate();
  }

  return (
    <Modal
      title={`${year}년 ${semester}학기 ${EXAM_TYPE_LABELS[examType]} 등록`}
      onClose={onClose}
    >
      <form onSubmit={submit} className="space-y-3">
        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">
            대상 반 ({selected.length}개 선택)
          </p>
          {selectable.length === 0 ? (
            <p className="text-sm text-slate-500">등록할 수 있는 반이 없습니다.</p>
          ) : (
            <div className="space-y-1">
              <button
                type="button"
                onClick={() =>
                  setSelected(
                    selected.length === selectable.length
                      ? []
                      : selectable.map((room) => room.id),
                  )
                }
                className="text-xs text-slate-500 underline"
              >
                {selected.length === selectable.length ? "전체 해제" : "전체 선택"}
              </button>
              <ul className="max-h-52 space-y-1 overflow-y-auto">
                {selectable.map((room) => (
                  <li key={room.id}>
                    <label className="flex items-center gap-2 text-sm text-slate-700">
                      <input
                        type="checkbox"
                        checked={selected.includes(room.id)}
                        onChange={(e) =>
                          setSelected((prev) =>
                            e.target.checked
                              ? [...prev, room.id]
                              : prev.filter((id) => id !== room.id),
                          )
                        }
                        className="h-4 w-4 rounded border-slate-300"
                      />
                      {room.name}
                    </label>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {registered.size > 0 && (
            <p className="mt-1 text-xs text-slate-400">
              이미 등록된 {registered.size}개 반은 목록에서 제외됩니다.
            </p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-2">
          <label className="text-sm text-slate-700">
            시작일
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="text-sm text-slate-700">
            종료일
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        <label className="block text-sm text-slate-700">
          시험 범위
          <textarea
            value={scopeNote}
            onChange={(e) => setScopeNote(e.target.value)}
            rows={2}
            placeholder="교과서 5~8과, 부교재 전 범위"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        {save.isError && <FormError message={errorMessage(save.error)} />}

        <SubmitButton pending={save.isPending} disabled={selected.length === 0}>
          {selected.length}개 반에 등록
        </SubmitButton>
      </form>
    </Modal>
  );
}
