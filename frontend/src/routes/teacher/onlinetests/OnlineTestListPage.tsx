import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../../shared/components/Badge";
import { SCORE_TYPE_LABELS } from "../../../shared/score/types";
import { listClassRooms, listOnlineTests } from "../api";
import OnlineTestCreateModal from "./OnlineTestCreateModal";

const NOW = new Date();

/** T-14 목록. 정답은 목록 응답에 없다 — 상세에서만 본다. */
export default function OnlineTestListPage() {
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState<number | "">("");
  const [creating, setCreating] = useState(false);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const params = {
    classRoomId: classRoomId === "" ? undefined : classRoomId,
    year,
    month: month === "" ? undefined : month,
  };
  const tests = useQuery({
    queryKey: ["teacher", "online-tests", params],
    queryFn: () => listOnlineTests(params),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">온라인 테스트 관리</h2>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          출제
        </button>
      </div>

      <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
        시험은 종이로 봅니다. 여기에는 정답 배열과 해설지만 등록하고, 학생은 답만 입력합니다.
      </p>

      <div className="grid grid-cols-3 gap-2 rounded-xl bg-white p-3 text-sm shadow-sm">
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
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear()].map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => setMonth(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          <option value="">전체 월</option>
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {m}월
            </option>
          ))}
        </select>
      </div>

      {tests.isPending || !tests.data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : tests.data.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          출제된 테스트가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {tests.data.map((test) => (
            <li key={test.testId}>
              <Link
                to={`/teacher/online-tests/${test.testId}`}
                className="block rounded-xl bg-white p-4 shadow-sm"
              >
                <p className="text-sm font-medium text-slate-900">{test.title}</p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {test.classRoomName} · {test.year}년 {test.month}월 {test.week}주차 ·{" "}
                  {test.questionCount}문항
                </p>
                <div className="mt-2 flex flex-wrap items-center gap-1.5">
                  {test.published ? (
                    <Badge tone="ok">공개</Badge>
                  ) : (
                    <Badge tone="warn">미공개</Badge>
                  )}
                  {test.scoreType ? (
                    <Badge>
                      성적 반영 · {SCORE_TYPE_LABELS[test.scoreType]} · {test.subject}
                    </Badge>
                  ) : (
                    <Badge>연습용</Badge>
                  )}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {creating && <OnlineTestCreateModal onClose={() => setCreating(false)} />}
    </div>
  );
}
