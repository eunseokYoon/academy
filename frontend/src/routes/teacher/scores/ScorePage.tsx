import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { WEEKLY_TEST_LABELS } from "../../../shared/score/types";
import type {
  TestResult,
  WeeklyTestCell,
  WeeklyTestType,
} from "../../../shared/score/types";
import { getWeeklyTestGrid, listClassRooms, saveWeeklyTests } from "../api";

const NOW = new Date();

/** 셀 하나의 화면 상태. 빈 문자열이 "미입력"이다 — 0과 구분해야 한다. */
interface CellDraft {
  correctCount: string;
  internalCorrect: string;
  externalCorrect: string;
  result: TestResult | "";
  retestPassed: boolean;
}

interface HeaderDraft {
  totalCount: string;
  internalTotal: string;
  externalTotal: string;
}

const EMPTY_CELL: CellDraft = {
  correctCount: "",
  internalCorrect: "",
  externalCorrect: "",
  result: "",
  retestPassed: false,
};

const EMPTY_HEADER: HeaderDraft = { totalCount: "", internalTotal: "", externalTotal: "" };

/** 열 하나가 차지하는 하위 칸. 헤더 colSpan과 tbody 셀 개수가 이 배열로 함께 결정된다. */
const COLUMN_FIELDS: Record<WeeklyTestType, string[]> = {
  WORD: ["맞힌수", "결과", "재시험"],
  REVIEW: ["결과", "재시험"],
  PRACTICE: ["맞힌수"],
  CLINIC: ["내부", "외부"],
};

/**
 * T-8. 반 × 주차 한 화면에서 4종을 전부 입력한다.
 *
 * <p><b>환산 점수를 화면에서 계산하지 마라.</b> 맞힌 개수와 전체 문항 수를 그대로 보내고
 * 정답률은 서버가 조회 시점에 계산한다 — 나중에 문항 수를 고쳐도 따라온다.
 *
 * <p>저장 후 입력칸을 비우지 마라. 수정하려고 다시 열었을 때 값이 있어야 한다.
 *
 * <p>종류를 하나 골라 한 종류씩 입력하는 방식으로 되돌리지 마라. 실제 운영은
 * 엑셀처럼 한 화면에서 다 채우는 것이다.
 */
export default function ScorePage() {
  const queryClient = useQueryClient();

  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState(1);
  const [classRoomId, setClassRoomId] = useState<number | "">("");

  /** testType → studentId → 셀 초안 */
  const [drafts, setDrafts] = useState<Record<string, Record<number, CellDraft>>>({});
  /** testType → 헤더 문항 수 초안 */
  const [headers, setHeaders] = useState<Record<string, HeaderDraft>>({});

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const grid = useQuery({
    queryKey: ["teacher", "weekly-tests", classRoomId, year, month, week],
    queryFn: () =>
      getWeeklyTestGrid({ classRoomId: Number(classRoomId), year, month, week }),
    enabled: classRoomId !== "",
  });

  // 서버 값을 초안으로 옮긴다. 반·주차가 바뀌면 앞 화면의 입력이 남으면 안 된다
  useEffect(() => {
    if (!grid.data) return;
    const nextDrafts: Record<string, Record<number, CellDraft>> = {};
    const nextHeaders: Record<string, HeaderDraft> = {};
    for (const column of grid.data.tests) {
      nextHeaders[column.testType] = {
        totalCount: column.totalCount?.toString() ?? "",
        internalTotal: column.internalTotal?.toString() ?? "",
        externalTotal: column.externalTotal?.toString() ?? "",
      };
      const cells: Record<number, CellDraft> = {};
      for (const cell of column.cells) {
        cells[cell.studentId] = {
          correctCount: cell.correctCount?.toString() ?? "",
          internalCorrect: cell.internalCorrect?.toString() ?? "",
          externalCorrect: cell.externalCorrect?.toString() ?? "",
          result: cell.result ?? "",
          retestPassed: cell.retestPassed,
        };
      }
      nextDrafts[column.testType] = cells;
    }
    setDrafts(nextDrafts);
    setHeaders(nextHeaders);
  }, [grid.data]);

  function cellOf(testType: WeeklyTestType, studentId: number): CellDraft {
    return drafts[testType]?.[studentId] ?? EMPTY_CELL;
  }

  function updateCell(testType: WeeklyTestType, studentId: number, patch: Partial<CellDraft>) {
    setDrafts((prev) => {
      const current = prev[testType]?.[studentId] ?? EMPTY_CELL;
      const next = { ...current, ...patch };
      // PASS로 바꾸면 재시험 체크를 되돌린다. 서버도 400으로 막지만 화면에서 먼저 막는다
      if (next.result !== "FAIL") next.retestPassed = false;
      return { ...prev, [testType]: { ...(prev[testType] ?? {}), [studentId]: next } };
    });
  }

  function updateHeader(testType: WeeklyTestType, patch: Partial<HeaderDraft>) {
    setHeaders((prev) => ({
      ...prev,
      [testType]: { ...(prev[testType] ?? EMPTY_HEADER), ...patch },
    }));
  }

  const toShort = (value: string) => (value.trim() === "" ? null : Number(value));

  const save = useMutation({
    mutationFn: () =>
      saveWeeklyTests({
        classRoomId: Number(classRoomId),
        year,
        month,
        week,
        tests: (grid.data?.tests ?? []).map((column) => ({
          testType: column.testType,
          totalCount: toShort(headers[column.testType]?.totalCount ?? ""),
          internalTotal: toShort(headers[column.testType]?.internalTotal ?? ""),
          externalTotal: toShort(headers[column.testType]?.externalTotal ?? ""),
          cells: (grid.data?.students ?? []).map((student): WeeklyTestCell => {
            const draft = cellOf(column.testType, student.studentId);
            return {
              studentId: student.studentId,
              correctCount: toShort(draft.correctCount),
              internalCorrect: toShort(draft.internalCorrect),
              externalCorrect: toShort(draft.externalCorrect),
              result: draft.result === "" ? null : draft.result,
              retestPassed: draft.retestPassed,
            };
          }),
        })),
      }),
    onSuccess: () => {
      // 입력칸을 비우지 않는다. 다시 불러와 채워진 상태를 유지한다
      void queryClient.invalidateQueries({
        queryKey: ["teacher", "weekly-tests", classRoomId, year, month, week],
      });
    },
  });

  /** Enter로 같은 열의 다음 학생 칸으로 내려간다. 마우스로 옮기면 20명 입력이 느려진다. */
  function focusNext(key: string, index: number) {
    const next = document.querySelector<HTMLInputElement>(`[data-cell="${key}-${index + 1}"]`);
    next?.focus();
    next?.select();
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">성적 기입</h2>

      <section className="space-y-2 rounded-xl bg-white p-4 shadow-sm">
        <select
          value={classRoomId}
          onChange={(e) => setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))}
          className="w-full rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
        >
          <option value="">반 선택</option>
          {(classRooms.data ?? []).map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>

        <div className="grid grid-cols-3 gap-2 text-sm">
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
              <option key={y} value={y}>{y}년</option>
            ))}
          </select>
          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>{m}월</option>
            ))}
          </select>
          <select
            value={week}
            onChange={(e) => setWeek(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {[1, 2, 3, 4, 5].map((w) => (
              <option key={w} value={w}>{w}주차</option>
            ))}
          </select>
        </div>
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
                {/* 학생 이름 열만 가로 스크롤에 고정된다. 없으면 360px에서 누구 점수인지 알 수 없다 */}
                <th
                  rowSpan={2}
                  className="sticky left-0 z-10 bg-slate-50 px-3 py-2 text-left font-medium"
                >
                  학생
                </th>
                {(grid.data?.tests ?? []).map((column) => (
                  <th
                    key={column.testType}
                    colSpan={COLUMN_FIELDS[column.testType].length}
                    className="border-l border-slate-200 px-3 py-2 text-left font-medium"
                  >
                    <div className="whitespace-nowrap text-slate-700">
                      {WEEKLY_TEST_LABELS[column.testType]}
                    </div>

                    {/* 전체 문항 수는 반 공통이라 열 헤더에서 한 번만 받는다 */}
                    {(column.testType === "WORD" || column.testType === "PRACTICE") && (
                      <label className="mt-1 flex items-center gap-1 font-normal">
                        전체
                        <input
                          type="number"
                          min={1}
                          value={headers[column.testType]?.totalCount ?? ""}
                          onChange={(e) =>
                            updateHeader(column.testType, { totalCount: e.target.value })
                          }
                          className="w-14 rounded border border-slate-300 px-1 py-0.5"
                        />
                      </label>
                    )}
                    {column.testType === "CLINIC" && (
                      <div className="mt-1 flex gap-1 font-normal">
                        <label className="flex items-center gap-1">
                          내부
                          <input
                            type="number"
                            min={1}
                            value={headers.CLINIC?.internalTotal ?? ""}
                            onChange={(e) =>
                              updateHeader("CLINIC", { internalTotal: e.target.value })
                            }
                            className="w-12 rounded border border-slate-300 px-1 py-0.5"
                          />
                        </label>
                        <label className="flex items-center gap-1">
                          외부
                          <input
                            type="number"
                            min={1}
                            value={headers.CLINIC?.externalTotal ?? ""}
                            onChange={(e) =>
                              updateHeader("CLINIC", { externalTotal: e.target.value })
                            }
                            className="w-12 rounded border border-slate-300 px-1 py-0.5"
                          />
                        </label>
                      </div>
                    )}
                  </th>
                ))}
              </tr>
              <tr>
                {(grid.data?.tests ?? []).flatMap((column) =>
                  COLUMN_FIELDS[column.testType].map((field, i) => (
                    <th
                      key={`${column.testType}-${field}`}
                      className={`px-3 py-1 text-left font-normal ${
                        i === 0 ? "border-l border-slate-200" : ""
                      }`}
                    >
                      {field}
                    </th>
                  )),
                )}
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
                  {(grid.data?.tests ?? []).map((column) => (
                    <CellGroup
                      key={column.testType}
                      type={column.testType}
                      index={index}
                      draft={cellOf(column.testType, student.studentId)}
                      onChange={(patch) =>
                        updateCell(column.testType, student.studentId, patch)
                      }
                      onEnter={(field) => focusNext(`${column.testType}-${field}`, index)}
                    />
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

/** 종류마다 칸 구성이 다르다. 분기는 여기 한 곳에만 둔다. */
function CellGroup({
  type,
  index,
  draft,
  onChange,
  onEnter,
}: {
  type: WeeklyTestType;
  index: number;
  draft: CellDraft;
  onChange: (patch: Partial<CellDraft>) => void;
  onEnter: (field: string) => void;
}) {
  const numberInput = (field: "correctCount" | "internalCorrect" | "externalCorrect") => (
    <input
      data-cell={`${type}-${field}-${index}`}
      type="number"
      inputMode="numeric"
      min={0}
      value={draft[field]}
      onChange={(e) => onChange({ [field]: e.target.value } as Partial<CellDraft>)}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          onEnter(field);
        }
      }}
      className="w-16 rounded-lg border border-slate-300 px-2 py-1"
    />
  );

  const resultSelect = (
    <select
      value={draft.result}
      onChange={(e) => onChange({ result: e.target.value as TestResult | "" })}
      className="rounded-lg border border-slate-300 bg-white px-1 py-1"
    >
      <option value="">—</option>
      <option value="PASS">P</option>
      <option value="FAIL">F</option>
    </select>
  );

  // 재시험 통과는 Fail을 받은 학생에게만 붙는다. 서버도 400으로 막지만 화면에서 먼저 막는다
  const retestBox = (
    <input
      type="checkbox"
      checked={draft.retestPassed}
      disabled={draft.result !== "FAIL"}
      onChange={(e) => onChange({ retestPassed: e.target.checked })}
      className="h-4 w-4 disabled:opacity-30"
    />
  );

  switch (type) {
    case "WORD":
      return (
        <>
          <td className="border-l border-slate-200 px-3 py-1.5">
            {numberInput("correctCount")}
          </td>
          <td className="px-3 py-1.5">{resultSelect}</td>
          <td className="px-3 py-1.5">{retestBox}</td>
        </>
      );
    case "REVIEW":
      return (
        <>
          <td className="border-l border-slate-200 px-3 py-1.5">{resultSelect}</td>
          <td className="px-3 py-1.5">{retestBox}</td>
        </>
      );
    case "PRACTICE":
      return (
        <td className="border-l border-slate-200 px-3 py-1.5">
          {numberInput("correctCount")}
        </td>
      );
    case "CLINIC":
      return (
        <>
          <td className="border-l border-slate-200 px-3 py-1.5">
            {numberInput("internalCorrect")}
          </td>
          <td className="px-3 py-1.5">{numberInput("externalCorrect")}</td>
        </>
      );
  }
}
