import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { listMyLessons } from "./api";

const NOW = new Date();

/**
 * S-5 목록. 재원 기간 밖 수업과 미공개 수업은 서버가 이미 걸러서 내려준다.
 * 프론트에서 다시 필터링하지 않는다.
 */
export default function StudentLessonPage() {
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState<number | "">("");
  const [page, setPage] = useState(0);

  const params = { year, month: month === "" ? undefined : month, page };
  const { data, isPending } = useQuery({
    queryKey: ["student", "lessons", params],
    queryFn: () => listMyLessons(params),
  });

  function changeMonth(value: number | "") {
    setMonth(value);
    setPage(0);
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">수업영상 및 레포트</h2>

      <div className="grid grid-cols-2 gap-2 rounded-xl bg-white p-3 text-sm shadow-sm">
        <select
          value={year}
          onChange={(e) => {
            setYear(Number(e.target.value));
            setPage(0);
          }}
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
          onChange={(e) => changeMonth(e.target.value === "" ? "" : Number(e.target.value))}
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

      {isPending || !data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : data.items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          공개된 수업이 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {data.items.map((lesson) => (
            <li key={lesson.lessonId}>
              <Link
                to={`/student/lessons/${lesson.lessonId}`}
                className="block rounded-xl bg-white p-4 shadow-sm"
              >
                <div className="flex items-center gap-2 text-xs text-slate-500">
                  {lesson.isNew && <Badge tone="danger">NEW</Badge>}
                  <span>{lesson.lessonDate.replace(/-/g, ".")}</span>
                  <span>·</span>
                  <span>{lesson.classRoomName}</span>
                </div>
                <p className="mt-1 text-sm font-medium text-slate-900">
                  {lesson.title ?? "제목 없음"}
                </p>
                <div className="mt-2 flex flex-wrap items-center gap-1.5">
                  {lesson.hasVideo ? (
                    lesson.viewed ? (
                      <Badge tone="ok">영상 시청 완료</Badge>
                    ) : (
                      <Badge tone="warn">영상 미시청</Badge>
                    )
                  ) : (
                    <Badge>영상 없음</Badge>
                  )}
                  {lesson.homeworkTitle && <Badge>숙제 · {lesson.homeworkTitle}</Badge>}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-slate-500">
            {page + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
