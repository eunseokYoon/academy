import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import {
  hasSchoolRank,
  REGULAR_EXAM_LABELS,
  REGULAR_EXAM_SLOTS,
} from "../../../shared/score/types";
import type { RegularExamSlot } from "../../../shared/score/types";
import { getRegularExamGrid, listClassRooms, saveRegularExams } from "../api";

const NOW = new Date();

/** 한 칸에 들어가는 세 값. 전부 문자열이라 "" 가 미입력이다. */
interface Cell {
  score: string;
  grade: string;
  rank: string;
}

const EMPTY_CELL: Cell = { score: "", grade: "", rank: "" };

const FIELDS: { key: keyof Cell; label: string; max?: number }[] = [
  { key: "score", label: "점수", max: 100 },
  { key: "grade", label: "등급", max: 9 },
  { key: "rank", label: "등수" },
];

const toNumber = (value: string) => (value.trim() === "" ? null : Number(value));

/**
 * 정기고사(학교 내신·모의고사). <b>선생님만 본다.</b>
 *
 * <p>학생·학부모 화면에 이 데이터를 노출하지 마라. 대응하는 조회 API 자체가 없다.
 *
 * <p>한 칸에 점수·등급·등수 셋이 세로로 쌓인다. 열로 펼치면 8슬롯 × 3 = 입력 24개가
 * 한 줄에 들어가 가로 스크롤이 생긴다.
 *
 * <p><b>등수는 내신 4개 슬롯에만 그린다.</b> 모의고사에 보내면 서버가 400을 준다 —
 * 판정 정본이 셋(여기·`RegularExamSlot.hasSchoolRank`·DB의 ck_res_rank_slot)이라
 * 갈라지면 화면에만 있는 칸이 저장에서 튕긴다.
 *
 * <p>등급·등수는 상대 지표 금지 규칙의 좁은 예외다 — <b>학교가 매긴 값을 받아 적는 것만</b>이고,
 * 학원이 계산하지 않는다. 과목·시험명은 없다. 값이 학생에 붙으므로 반을 옮겨도 남는다.
 */
export default function RegularExamPage() {
  const queryClient = useQueryClient();

  const [year, setYear] = useState(NOW.getFullYear());
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  /** studentId → slot → 세 칸. 빈 문자열이 "미입력"이고, 셋 다 비면 그 행을 지운다 */
  const [drafts, setDrafts] = useState<Record<number, Partial<Record<RegularExamSlot, Cell>>>>({});

  const setCell = (studentId: number, slot: RegularExamSlot, field: keyof Cell, value: string) =>
    setDrafts((prev) => ({
      ...prev,
      [studentId]: {
        ...(prev[studentId] ?? {}),
        [slot]: { ...(prev[studentId]?.[slot] ?? EMPTY_CELL), [field]: value },
      },
    }));

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
    const next: Record<number, Partial<Record<RegularExamSlot, Cell>>> = {};
    for (const score of grid.data.scores) {
      next[score.studentId] = {
        ...(next[score.studentId] ?? {}),
        [score.examSlot]: {
          score: score.rawScore?.toString() ?? "",
          grade: score.grade?.toString() ?? "",
          rank: score.schoolRank?.toString() ?? "",
        },
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
            const cell = drafts[student.studentId]?.[slot] ?? EMPTY_CELL;
            return {
              studentId: student.studentId,
              examSlot: slot,
              // 셋 다 비우면 그 칸의 행을 삭제한다
              rawScore: toNumber(cell.score),
              grade: toNumber(cell.grade),
              // 모의고사에 등수를 실어 보내면 400이다. 화면에 칸이 없어도 값이 남아 있을 수 있다
              schoolRank: hasSchoolRank(slot) ? toNumber(cell.rank) : null,
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

  /** Enter로 같은 고사·같은 항목의 다음 학생 칸으로 내려간다. */
  function focusNext(slot: RegularExamSlot, field: keyof Cell, index: number) {
    const next = document.querySelector<HTMLInputElement>(
      `[data-cell="${slot}-${field}-${index + 1}"]`,
    );
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
                  {REGULAR_EXAM_SLOTS.map((slot) => {
                    const cell = drafts[student.studentId]?.[slot] ?? EMPTY_CELL;
                    return (
                      <td key={slot} className="px-3 py-1.5 align-top">
                        <div className="flex flex-col gap-1">
                          {FIELDS.map((field) => {
                            // 모의고사에는 등수가 없다. 빈 칸으로 두면 "왜 못 쓰지"가 된다
                            if (field.key === "rank" && !hasSchoolRank(slot)) return null;
                            return (
                              <label key={field.key} className="flex items-center gap-1">
                                <span className="w-6 shrink-0 text-[11px] text-slate-400">
                                  {field.label}
                                </span>
                                <input
                                  data-cell={`${slot}-${field.key}-${index}`}
                                  type="number"
                                  inputMode="numeric"
                                  min={field.key === "score" ? 0 : 1}
                                  max={field.max}
                                  value={cell[field.key]}
                                  onChange={(e) =>
                                    setCell(student.studentId, slot, field.key, e.target.value)
                                  }
                                  onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                      e.preventDefault();
                                      focusNext(slot, field.key, index);
                                    }
                                  }}
                                  className="w-14 rounded-lg border border-slate-300 px-2 py-1"
                                />
                              </label>
                            );
                          })}
                        </div>
                      </td>
                    );
                  })}
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
