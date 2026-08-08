import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { formatDueAt } from "../../../shared/homework/types";
import type { HomeworkResult } from "../../../shared/homework/types";
import {
  closeResubmit,
  getHomeworkGrid,
  listClassRooms,
  listLessons,
  openResubmit,
  saveHomeworkGrid,
} from "../api";
import { GradeCell } from "./GradeCell";

/** 셀 초안. result가 null이면 미채점이다 — 0%와 구분해야 한다. */
interface CellDraft {
  result: HomeworkResult | null;
  completionRate: number | null;
}

const EMPTY_CELL: CellDraft = { result: null, completionRate: null };

/**
 * T-6b. 반 × 수업일 그리드 한 장에서 숙제를 채점한다.
 *
 * <p>열 제목은 선생님이 직접 입력한다. 숙제가 매주 달라서 고정 목록을 둘 수 없다.
 *
 * <p><b>열을 지우는 버튼을 여기 두지 마라.</b> 배열에서 빼도 서버는 지우지 않고,
 * 삭제는 숙제 목록의 삭제 버튼(채점·제출이 있으면 409)뿐이다.
 */
export default function HomeworkGridPage() {
  const queryClient = useQueryClient();

  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [lessonId, setLessonId] = useState<number | "">("");

  /** 열 인덱스 → studentId → 초안 */
  const [drafts, setDrafts] = useState<Record<number, Record<number, CellDraft>>>({});
  /** 열 인덱스 → { homeworkId, title } */
  const [columns, setColumns] = useState<{ homeworkId: number | null; title: string }[]>([]);
  const [error, setError] = useState<string | null>(null);
  /**
   * 저장하지 않은 입력이 있는가. 아래 동기화 effect가 이 값으로 자기 자신을 막는다.
   *
   * <p>재제출 요청·취소도 그리드 쿼리를 무효화한다. 그때 서버 값으로 초안을 덮으면
   * 방금 찍은 채점이 <b>경고도 에러도 없이</b> 사라진다.
   */
  const [dirty, setDirty] = useState(false);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const lessons = useQuery({
    queryKey: ["teacher", "lessons", classRoomId],
    queryFn: () => listLessons({ classRoomId: Number(classRoomId) }),
    enabled: classRoomId !== "",
  });

  const grid = useQuery({
    queryKey: ["teacher", "homework-grid", lessonId],
    queryFn: () => getHomeworkGrid(Number(lessonId)),
    enabled: lessonId !== "",
  });

  // 서버 값을 초안으로 옮긴다. 수업일이 바뀌면 앞 화면의 입력이 남으면 안 된다 —
  // grid.data가 새 lessonId로 교체될 때마다 이 effect가 다시 돌아 옛 초안을 덮어쓴다
  useEffect(() => {
    if (!grid.data) return;
    // 저장 안 한 입력이 있으면 서버 값으로 덮지 않는다. 수업일을 바꿀 때는
    // 선택 핸들러가 dirty를 먼저 내리므로 여기까지 내려와 정상적으로 초기화된다
    if (dirty) return;
    const nextColumns = grid.data.columns.map((column) => ({
      homeworkId: column.homeworkId,
      title: column.title,
    }));
    const nextDrafts: Record<number, Record<number, CellDraft>> = {};
    grid.data.columns.forEach((column, index) => {
      const cells: Record<number, CellDraft> = {};
      for (const cell of column.cells) {
        cells[cell.studentId] = {
          result: cell.result,
          completionRate: cell.completionRate,
        };
      }
      nextDrafts[index] = cells;
    });
    setColumns(nextColumns);
    setDrafts(nextDrafts);
  }, [grid.data, dirty]);

  const save = useMutation({
    mutationFn: () =>
      saveHomeworkGrid({
        lessonId: Number(lessonId),
        columns: columns.map((column, index) => ({
          homeworkId: column.homeworkId,
          title: column.title.trim(),
          sortOrder: index + 1,
          cells: (grid.data?.students ?? []).map((student) => {
            const draft = drafts[index]?.[student.studentId] ?? EMPTY_CELL;
            return {
              studentId: student.studentId,
              result: draft.result,
              completionRate: draft.result === "PARTIAL" ? draft.completionRate : null,
            };
          }),
        })),
      }),
    onSuccess: () => {
      setError(null);
      // 저장했으니 서버가 정본이다. dirty를 먼저 내려야 아래 refetch가 초안에 반영된다 —
      // 새 열의 id가 그 경로로만 들어온다
      setDirty(false);
      // 입력칸을 비우지 않는다. 다시 불러와 채워진 상태(+ 새 열의 id)를 유지한다
      void queryClient.invalidateQueries({ queryKey: ["teacher", "homework-grid", lessonId] });
    },
    onError: (e) => setError(errorMessage(e, "저장하지 못했습니다.")),
  });

  const requestResubmit = useMutation({
    mutationFn: (homeworkId: number) => openResubmit(homeworkId, null),
    onSuccess: () => {
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ["teacher", "homework-grid", lessonId] });
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const cancelResubmit = useMutation({
    mutationFn: (homeworkId: number) => closeResubmit(homeworkId),
    onSuccess: () => {
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ["teacher", "homework-grid", lessonId] });
    },
    onError: (e) => setError(errorMessage(e)),
  });

  function updateCell(columnIndex: number, studentId: number, patch: CellDraft) {
    setDirty(true);
    setDrafts((prev) => ({
      ...prev,
      [columnIndex]: { ...(prev[columnIndex] ?? {}), [studentId]: patch },
    }));
  }

  function addColumn() {
    setDirty(true);
    setColumns((prev) => [...prev, { homeworkId: null, title: "" }]);
  }

  function renameColumn(index: number, title: string) {
    setDirty(true);
    setColumns((prev) => prev.map((c, i) => (i === index ? { ...c, title } : c)));
  }

  const students = grid.data?.students ?? [];

  /**
   * PARTIAL인데 퍼센트가 1~99를 벗어난(또는 비어 있는) 칸을 찾는다.
   *
   * <p>PUT은 그리드 한 장을 통째로 보낸다. 칸 하나가 서버 CHECK(ck_submissions_rate)에
   * 걸리면 요청 전체가 400이 되고, 방금 20명 × 4열을 채점한 게 전부 날아간다.
   * 그래서 어느 학생·어느 열인지까지 짚어서 저장 전에 막는다.
   */
  function findInvalidCompletionRate(): string | null {
    for (let index = 0; index < columns.length; index++) {
      const title = columns[index].title.trim() || `${index + 1}번째 열`;
      for (const student of students) {
        const draft = drafts[index]?.[student.studentId] ?? EMPTY_CELL;
        if (draft.result !== "PARTIAL") continue;
        const rate = draft.completionRate;
        if (rate === null || rate < 1 || rate > 99) {
          return `${title} · ${student.name}의 퍼센트를 1~99 사이로 입력하세요.`;
        }
      }
    }
    return null;
  }

  /**
   * 저장 전 검사 두 가지(제목, 퍼센트)를 통과해야 실제로 보낸다.
   *
   * <p>검사에 걸려 막을 때는 반드시 save.reset()도 같이 부른다 — 안 그러면 "한 번 저장
   * 성공 → 열 추가 → 제목 안 채우고 저장" 순서에서 새 에러와 옛 "저장되었습니다."가
   * 동시에 떠서 뭐가 맞는 상태인지 알 수 없다.
   */
  function handleSave() {
    if (columns.some((column) => column.title.trim() === "")) {
      setError("모든 열에 제목을 입력하세요.");
      save.reset();
      return;
    }
    const rateError = findInvalidCompletionRate();
    if (rateError) {
      setError(rateError);
      save.reset();
      return;
    }
    setError(null);
    save.mutate();
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">숙제 채점</h2>

      <div className="flex flex-wrap gap-2">
        <select
          value={classRoomId}
          onChange={(e) => {
            setClassRoomId(e.target.value === "" ? "" : Number(e.target.value));
            // 반이 바뀌면 이전 반의 수업일 선택도 무의미하다 — 같이 비운다.
            // dirty도 내린다. 안 내리면 앞 반의 초안이 다음 반 표에 그대로 남는다
            setLessonId("");
            setDirty(false);
          }}
          className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
        >
          <option value="">반 선택</option>
          {(classRooms.data ?? []).map((room) => (
            <option key={room.classRoomId} value={room.classRoomId}>
              {room.name}
            </option>
          ))}
        </select>

        <select
          value={lessonId}
          onChange={(e) => {
            setLessonId(e.target.value === "" ? "" : Number(e.target.value));
            setDirty(false);
          }}
          disabled={classRoomId === ""}
          className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm disabled:bg-slate-100"
        >
          <option value="">수업일 선택</option>
          {(lessons.data ?? []).map((lesson) => (
            <option key={lesson.lessonId} value={lesson.lessonId}>
              {lesson.lessonDate}
            </option>
          ))}
        </select>
      </div>

      {lessonId === "" ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          반과 수업일을 고르면 숙제 표가 열립니다.
        </p>
      ) : grid.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl bg-white shadow-sm">
            <table className="min-w-full border-collapse text-sm">
              <thead>
                <tr>
                  {/* 학생 이름 열만 가로 스크롤에 고정된다. 없으면 360px에서 누구 칸인지 알 수 없다 */}
                  <th className="sticky left-0 z-10 bg-white px-2 py-2 text-left">이름</th>
                  {columns.map((column, index) => {
                    const server = grid.data?.columns[index];
                    return (
                      <th key={index} className="min-w-[120px] px-2 py-2 align-top font-normal">
                        <input
                          value={column.title}
                          placeholder="숙제 제목"
                          onChange={(e) => renameColumn(index, e.target.value)}
                          className="w-full rounded border border-slate-300 px-1 py-1 text-xs"
                        />
                        {/* 저장 전 새 열(homeworkId 없음)에는 재제출 버튼을 달지 않는다 —
                            아직 서버에 없는 숙제라 요청할 대상이 없다 */}
                        {server && (
                          <div className="mt-1">
                            {server.resubmitDueAt === null ? (
                              <button
                                type="button"
                                disabled={requestResubmit.isPending}
                                onClick={() => requestResubmit.mutate(server.homeworkId)}
                                className="rounded bg-slate-900 px-2 py-1 text-[11px] text-white
                                           disabled:opacity-50"
                              >
                                재제출 요청
                              </button>
                            ) : (
                              <div className="flex flex-col items-start gap-0.5">
                                <span className="text-[11px] text-slate-500">
                                  재제출 {server.resubmitTargetCount}명 ·{" "}
                                  {formatDueAt(server.resubmitDueAt)} 마감
                                </span>
                                <button
                                  type="button"
                                  disabled={cancelResubmit.isPending}
                                  onClick={() => cancelResubmit.mutate(server.homeworkId)}
                                  className="rounded bg-slate-200 px-2 py-1 text-[11px] text-slate-700
                                             disabled:opacity-50"
                                >
                                  취소
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                      </th>
                    );
                  })}
                  <th className="px-2 py-2">
                    <button
                      type="button"
                      onClick={addColumn}
                      className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-700"
                    >
                      + 열 추가
                    </button>
                  </th>
                </tr>
              </thead>
              <tbody>
                {students.map((student) => (
                  <tr key={student.studentId} className="border-t border-slate-100">
                    <td className="sticky left-0 z-10 bg-white px-2 py-2 whitespace-nowrap">
                      {student.name}
                    </td>
                    {columns.map((_, index) => {
                      const draft = drafts[index]?.[student.studentId] ?? EMPTY_CELL;
                      const server = grid.data?.columns[index]?.cells.find(
                        (cell) => cell.studentId === student.studentId,
                      );
                      return (
                        <td key={index} className="px-2 py-2">
                          <GradeCell
                            result={draft.result}
                            completionRate={draft.completionRate}
                            // 마크를 DONE에서 다른 값으로 바꾸는 순간 "재제출" 표시는 의미가
                            // 없어진다. 저장 전이라도 화면에서 먼저 내린다
                            resolvedByResubmission={
                              draft.result === "DONE" && (server?.resolvedByResubmission ?? false)
                            }
                            onChange={(result, completionRate) =>
                              updateCell(index, student.studentId, { result, completionRate })
                            }
                          />
                        </td>
                      );
                    })}
                    <td />
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 에러·완료 메시지는 저장 버튼(하단 고정바) 바로 위에 둔다. 표가 길면
              위쪽에 있던 메시지는 저장을 누른 뒤 스크롤을 올려야만 보인다 — 특히
              퍼센트 오류는 어느 칸인지 안 보고 넘어가기 쉽다. ScorePage.tsx와 같은 위치다 */}
          <FormError message={error} />
          {save.isSuccess && <p className="text-sm text-emerald-700">저장되었습니다.</p>}

          <div className="sticky bottom-0 flex items-center justify-between gap-3 border-t
                          border-slate-200 bg-white p-3">
            <span className="text-sm text-slate-500">{students.length}명</span>
            <button
              type="button"
              onClick={handleSave}
              disabled={save.isPending}
              className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white
                         disabled:opacity-50"
            >
              {save.isPending ? "저장 중…" : "저장"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
