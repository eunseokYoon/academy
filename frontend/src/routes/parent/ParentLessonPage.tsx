import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { getChildLessons } from "./api";

const NOW = new Date();

/**
 * P-5 목록. 학생 화면(S-5)과 같은 수업을 보되 <b>영상 관련 표시가 없다.</b>
 *
 * <p>"영상 시청 완료" 같은 배지를 붙이지 마라. hasVideo·viewed가 응답에 오긴 하지만
 * 학부모에게는 언제나 null이다 — false가 아니라 null인 건 "영상이 없다"가 아니라
 * "학부모는 못 본다"는 뜻이기 때문이다. 학부모가 보는 건 레포트까지다.
 */
export default function ParentLessonPage() {
  const { children, selectedStudentId, setSelectedStudentId } = useSelectedChild();
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState<number | "">("");
  const [page, setPage] = useState(0);

  const params = { year, month: month === "" ? undefined : month, page };
  const { data, isPending } = useQuery({
    queryKey: ["parent", "lessons", selectedStudentId, params],
    queryFn: () => getChildLessons(selectedStudentId!, params),
    enabled: selectedStudentId !== null,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">수업 레포트</h2>
        {children.length > 1 && (
          <select
            value={selectedStudentId ?? ""}
            onChange={(e) => {
              setSelectedStudentId(Number(e.target.value));
              setPage(0);
            }}
            className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
          >
            {children.map((child) => (
              <option key={child.studentId} value={child.studentId}>
                {child.name}
              </option>
            ))}
          </select>
        )}
      </div>

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
          onChange={(e) => {
            setMonth(e.target.value === "" ? "" : Number(e.target.value));
            setPage(0);
          }}
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
                to={`/parent/lessons/${lesson.lessonId}`}
                className="block rounded-xl bg-white p-4 shadow-sm"
              >
                <div className="flex items-center gap-2 text-xs text-slate-500">
                  <span>{lesson.lessonDate.replace(/-/g, ".")}</span>
                  <span>·</span>
                  <span>{lesson.classRoomName}</span>
                </div>
                <p className="mt-1 text-sm font-medium text-slate-900">
                  {lesson.title ?? "제목 없음"}
                </p>
                {lesson.homeworkTitle && (
                  <div className="mt-2">
                    <Badge>숙제 · {lesson.homeworkTitle}</Badge>
                  </div>
                )}
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
          <span className="tnum text-slate-500">
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
