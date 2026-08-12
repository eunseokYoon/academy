import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { REGULAR_EXAM_LABELS, REGULAR_EXAM_SLOTS } from "../../../shared/score/types";
import type { RegularExamSlot } from "../../../shared/score/types";
import { getRegularExamGrid, listClassRooms, saveRegularExams } from "../api";

const NOW = new Date();

/**
 * 정기고사(학교 내신·모의고사). <b>선생님만 본다.</b>
 *
 * <p>학생·학부모 화면에 이 데이터를 노출하지 마라. 대응하는 조회 API 자체가 없다.
 *
 * <p>원점수만 기록한다. 등급·과목·시험명은 없다. 점수가 학생에 붙으므로
 * 반을 옮겨도 값은 남는다.
 */
export default function RegularExamPage() {
  const queryClient = useQueryClient();

  const [year, setYear] = useState(NOW.getFullYear());
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  /** studentId → slot → 입력값. 빈 문자열이 "미입력"이다 */
  const [drafts, setDrafts] = useState<Record<number, Partial<Record<RegularExamSlot, string>>>>(
    {},
  );

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const grid = useQuery({
    queryKey: ["teacher", "regular-exams", classRoomId, year],
    queryFn: () => getRegularExamGrid({ classRoomId: Number(classRoomId), year }),
    enabled: classRoomId !== "",
  });

  // 반·연도가 바뀌면 앞 화면의 입력이 남으면 안 된다
  useEffect(() => {
    if (!grid.data) return;
    const next: Record<number, Partial<Record<RegularExamSlot, string>>> = {};
    for (const score of grid.data.scores) {
      next[score.studentId] = {
        ...(next[score.studentId] ?? {}),
        [score.examSlot]: score.rawScore.toString(),
      };
    }
    setDrafts(next);
  }, [grid.data]);

  const save = useMutation({
    mutationFn: () =>
      saveRegularExams({
        classRoomId: Number(classRoomId),
        year,
        scores: (grid.data?.students ?? []).flatMap((student) =>
          REGULAR_EXAM_SLOTS.map((slot) => {
            const value = drafts[student.studentId]?.[slot] ?? "";
            return {
              studentId: student.studentId,
              examSlot: slot,
              // 비우면 그 칸의 행을 삭제한다
              rawScore: value.trim() === "" ? null : Number(value),
            };
          }),
        ),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["teacher", "regular-exams", classRoomId, year],
      });
    },
  });

  /** Enter로 같은 고사의 다음 학생 칸으로 내려간다. */
  function focusNext(slot: RegularExamSlot, index: number) {
    const next = document.querySelector<HTMLInputElement>(`[data-cell="${slot}-${index + 1}"]`);
    next?.focus();
    next?.select();
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">정기고사</h2>
        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
          선생님만 볼 수 있습니다
        </span>
      </div>

      <section className="grid grid-cols-2 gap-2 rounded-xl bg-white p-4 text-sm shadow-sm">
        <select
          value={classRoomId}
          onChange={(e) => setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          <option value="">반 선택</option>
          {(classRooms.data ?? []).map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
            <option key={y} value={y}>{y}년</option>
          ))}
        </select>
      </section>

      {classRoomId === "" ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          반을 선택하세요.
        </p>
      ) : grid.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <section className="overflow-x-auto rounded-xl bg-white shadow-sm">
          <table className="min-w-max text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="sticky left-0 z-10 bg-slate-50 px-3 py-2 text-left font-medium">
                  학생
                </th>
                {REGULAR_EXAM_SLOTS.map((slot) => (
                  <th key={slot} className="px-3 py-2 text-left font-medium whitespace-nowrap">
                    {REGULAR_EXAM_LABELS[slot]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {(grid.data?.students ?? []).map((student, index) => (
                <tr key={student.studentId}>
                  <td className="sticky left-0 z-10 bg-white px-3 py-1.5 text-slate-900">
                    {student.name}
                    {!student.enrolled && (
                      <span className="ml-1 text-xs text-slate-400">퇴원</span>
                    )}
                  </td>
                  {REGULAR_EXAM_SLOTS.map((slot) => (
                    <td key={slot} className="px-3 py-1.5">
                      <input
                        data-cell={`${slot}-${index}`}
                        type="number"
                        inputMode="numeric"
                        min={0}
                        max={100}
                        value={drafts[student.studentId]?.[slot] ?? ""}
                        onChange={(e) =>
                          setDrafts((prev) => ({
                            ...prev,
                            [student.studentId]: {
                              ...(prev[student.studentId] ?? {}),
                              [slot]: e.target.value,
                            },
                          }))
                        }
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            e.preventDefault();
                            focusNext(slot, index);
                          }
                        }}
                        className="w-16 rounded-lg border border-slate-300 px-2 py-1"
                      />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {save.isError && <FormError message={errorMessage(save.error)} />}
      {save.isSuccess && <p className="text-sm text-emerald-700">저장되었습니다.</p>}

      {classRoomId !== "" && (
        <div className="sticky bottom-0 flex items-center justify-between gap-3 border-t
                        border-slate-200 bg-white p-3">
          <span className="text-sm text-slate-500">
            {grid.data?.students.length ?? 0}명
          </span>
          <button
            type="button"
            disabled={save.isPending}
            onClick={() => save.mutate()}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white
                       disabled:opacity-50"
          >
            {save.isPending ? "저장 중…" : "저장"}
          </button>
        </div>
      )}
    </div>
  );
}
