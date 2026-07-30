import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { SCORE_TYPE_LABELS } from "../../../shared/score/types";
import type { ScoreType } from "../../../shared/score/types";
import {
  bulkCreateScores,
  listClassRoomStudents,
  listClassRooms,
  listExamSchedules,
} from "../api";
import { today } from "../format";

const NOW = new Date();

interface Row {
  studentId: number;
  name: string;
  /** 화면 입력값. WORD는 맞힌 개수, 그 외는 원점수다. */
  raw: string;
  grade: string;
}

/**
 * T-8. 주차를 먼저 고르고 반 명단 표에 점수만 순서대로 입력한다.
 *
 * <p><b>단어 시험은 화면에서 100점 만점으로 환산한다.</b> 문항 수를 먼저 받고
 * 맞힌 개수를 입력받아 환산값을 보낸다 — 원점수를 그대로 보내면 P-4 그래프 세로축이 무너진다.
 *
 * <p>한 명씩 저장 버튼을 누르는 방식으로 만들지 마라. 200명이면 실사용이 불가능하다.
 */
export default function ScorePage() {
  const queryClient = useQueryClient();

  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState(1);
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [scoreType, setScoreType] = useState<ScoreType>("WORD");
  const [examName, setExamName] = useState("");
  const [subject, setSubject] = useState("영어");
  const [examDate, setExamDate] = useState(today());
  const [examScheduleId, setExamScheduleId] = useState<number | "">("");
  const [questionCount, setQuestionCount] = useState("25");
  const [rows, setRows] = useState<Row[]>([]);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const schedules = useQuery({
    queryKey: ["teacher", "exam-schedules", classRoomId, year],
    queryFn: () => listExamSchedules({ classRoomId: Number(classRoomId), year }),
    enabled: scoreType === "INTERNAL" && classRoomId !== "",
  });

  const roster = useQuery({
    queryKey: ["teacher", "class-room-students", classRoomId],
    queryFn: () => listClassRoomStudents(Number(classRoomId)),
    enabled: classRoomId !== "",
  });

  // 명단이 바뀌면 입력 행을 다시 만든다. 반을 바꿨는데 앞 반의 점수가 남아 있으면 사고다
  const rosterKey = roster.data?.students.map((s) => s.studentId).join(",") ?? "";
  useEffect(() => {
    setRows(
      (roster.data?.students ?? []).map((student) => ({
        studentId: student.studentId,
        name: student.name,
        raw: "",
        grade: "",
      })),
    );
    // roster.data를 의존성에 넣으면 refetch마다 입력이 날아간다. 명단 구성이 바뀔 때만 초기화한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rosterKey]);

  const questions = Number(questionCount);
  const wordScale = scoreType === "WORD" && Number.isFinite(questions) && questions > 0;

  /** 25문항 중 20개 → 80.00. 환산은 여기서 하고 DB에는 환산값만 들어간다. */
  function toRawScore(row: Row): number | null {
    if (row.raw.trim() === "") return null;
    const value = Number(row.raw);
    if (!Number.isFinite(value)) return null;
    if (scoreType === "WORD") {
      return wordScale ? Math.round((value / questions) * 10000) / 100 : null;
    }
    return value;
  }

  const filled = rows.filter((row) => row.raw.trim() !== "" || row.grade.trim() !== "");

  const save = useMutation({
    mutationFn: () =>
      bulkCreateScores({
        scoreType,
        examScheduleId: scoreType === "INTERNAL" && examScheduleId !== ""
          ? Number(examScheduleId)
          : null,
        examName: examName.trim(),
        subject: subject.trim(),
        examDate,
        year,
        month,
        week,
        scores: filled.map((row) => ({
          studentId: row.studentId,
          rawScore: toRawScore(row),
          gradeLevel: row.grade.trim() === "" ? null : Number(row.grade),
          memo: null,
        })),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["teacher", "students"] });
      setRows((prev) => prev.map((row) => ({ ...row, raw: "", grade: "" })));
    },
  });

  function updateRow(index: number, patch: Partial<Row>) {
    setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  /** Enter로 다음 학생 칸으로 내려간다. 마우스로 칸을 옮기면 20명 입력이 느려진다. */
  function focusNext(index: number, field: "raw" | "grade") {
    const next = document.querySelector<HTMLInputElement>(
      `[data-cell="${field}-${index + 1}"]`,
    );
    next?.focus();
    next?.select();
  }

  const canSave =
    classRoomId !== "" && examName.trim() !== "" && subject.trim() !== "" && filled.length > 0
    && (scoreType !== "WORD" || wordScale);

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">주차별 성적 입력</h2>

      <section className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
        <div className="grid grid-cols-3 gap-2 text-sm">
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
            onChange={(e) => setWeek(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {[1, 2, 3, 4, 5].map((w) => (
              <option key={w} value={w}>
                {w}주차
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
          <select
            value={classRoomId}
            onChange={(e) =>
              setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))
            }
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
            value={scoreType}
            onChange={(e) => setScoreType(e.target.value as ScoreType)}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {(Object.keys(SCORE_TYPE_LABELS) as ScoreType[]).map((type) => (
              <option key={type} value={type}>
                {SCORE_TYPE_LABELS[type]}
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
          <input
            value={examName}
            onChange={(e) => setExamName(e.target.value)}
            placeholder="시험명 (예: 5월 3주차 단어시험)"
            className="rounded-lg border border-slate-300 px-3 py-2 sm:col-span-2"
          />
          <input
            type="date"
            value={examDate}
            onChange={(e) => setExamDate(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </div>

        <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
          {/* 성적 범위가 미확정이라 드롭다운 + 직접 입력이다. 영어로 고정하지 않는다 */}
          <input
            list="score-subjects"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder="과목"
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
          <datalist id="score-subjects">
            <option value="영어" />
            <option value="국어" />
            <option value="수학" />
          </datalist>

          {scoreType === "WORD" && (
            <label className="flex items-center gap-2">
              <span className="shrink-0 text-slate-600">문항 수</span>
              <input
                type="number"
                min={1}
                value={questionCount}
                onChange={(e) => setQuestionCount(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
              />
            </label>
          )}

          {scoreType === "INTERNAL" && (
            <select
              value={examScheduleId}
              onChange={(e) =>
                setExamScheduleId(e.target.value === "" ? "" : Number(e.target.value))
              }
              className="rounded-lg border border-slate-300 bg-white px-2 py-2"
            >
              <option value="">시험 일정 연결 없음</option>
              {(schedules.data ?? []).map((schedule) => (
                <option key={schedule.examScheduleId} value={schedule.examScheduleId}>
                  {schedule.year}년 {schedule.semester}학기{" "}
                  {schedule.examType === "MIDTERM" ? "중간" : "기말"}
                </option>
              ))}
            </select>
          )}
        </div>

        {scoreType === "WORD" && (
          <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
            맞힌 개수를 입력하면 100점 만점으로 환산해 저장합니다. 저장되는 값은 환산 점수입니다.
          </p>
        )}
      </section>

      {classRoomId === "" ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          반을 선택하세요.
        </p>
      ) : roster.isPending ? (
        <p className="text-sm text-slate-400">명단 불러오는 중…</p>
      ) : (
        <section className="overflow-hidden rounded-xl bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="px-3 py-2 text-left font-medium">학생</th>
                <th className="px-3 py-2 text-left font-medium">
                  {scoreType === "WORD" ? `맞힌 개수 (/${questionCount})` : "원점수"}
                </th>
                {scoreType !== "WORD" && (
                  <th className="px-3 py-2 text-left font-medium">등급</th>
                )}
                {scoreType === "WORD" && (
                  <th className="px-3 py-2 text-left font-medium">환산</th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((row, index) => (
                <tr key={row.studentId}>
                  <td className="px-3 py-1.5 text-slate-900">{row.name}</td>
                  <td className="px-3 py-1.5">
                    <input
                      data-cell={`raw-${index}`}
                      type="number"
                      inputMode="numeric"
                      value={row.raw}
                      onChange={(e) => updateRow(index, { raw: e.target.value })}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          focusNext(index, "raw");
                        }
                      }}
                      className="w-20 rounded-lg border border-slate-300 px-2 py-1"
                    />
                  </td>
                  {scoreType !== "WORD" && (
                    <td className="px-3 py-1.5">
                      <input
                        data-cell={`grade-${index}`}
                        type="number"
                        min={1}
                        max={9}
                        value={row.grade}
                        onChange={(e) => updateRow(index, { grade: e.target.value })}
                        onKeyDown={(e) => {
                          if (e.key === "Enter") {
                            e.preventDefault();
                            focusNext(index, "grade");
                          }
                        }}
                        className="w-16 rounded-lg border border-slate-300 px-2 py-1"
                      />
                    </td>
                  )}
                  {scoreType === "WORD" && (
                    <td className="px-3 py-1.5 text-slate-500">
                      {row.raw.trim() === "" ? "—" : `${toRawScore(row) ?? "—"}점`}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {save.isError && <FormError message={errorMessage(save.error)} />}
      {save.isSuccess && (
        <p className="text-sm text-emerald-700">
          {save.data.created}건 저장, {save.data.updated}건 수정되었습니다.
        </p>
      )}

      {classRoomId !== "" && (
        <div className="sticky bottom-0 flex items-center justify-between gap-3 border-t
                        border-slate-200 bg-white p-3">
          <span className="text-sm text-slate-500">
            {rows.length}명 중 {filled.length}명 입력됨
          </span>
          <button
            type="button"
            disabled={!canSave || save.isPending}
            onClick={() => save.mutate()}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white
                       disabled:opacity-50"
          >
            {save.isPending ? "저장 중…" : "일괄 저장"}
          </button>
        </div>
      )}
    </div>
  );
}
