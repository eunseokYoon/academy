import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import type { ReactNode } from "react";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { DAY_STATUS_STYLE } from "../../shared/attendance/types";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { SUBMISSION_LABELS, formatDueAt } from "../../shared/homework/types";
import { getChildLesson } from "./api";

/**
 * P-5 상세. 학생 화면(S-5)과 같은 레포트를 보되 <b>영상 영역이 없다.</b>
 *
 * <p>영상 재생 버튼이나 iframe을 여기 붙이지 마라. 응답에 embedUrl 필드는 있지만
 * 학부모에게는 언제나 null이다 — 서버가 forParent 팩토리에서 고정해 보낸다.
 *
 * <p>숙제는 제목·마감·제출 여부까지다. 숙제 지시문(description)·사진·피드백은 오지 않는다 —
 * P-3 숙제 목록과 같은 규칙이다. 학생 숙제 화면으로 링크를 걸지도 마라.
 */
export default function ParentLessonDetailPage() {
  const { lessonId } = useParams();
  const { selectedStudentId } = useSelectedChild();
  const id = Number(lessonId);

  const { data, isPending, isError } = useQuery({
    queryKey: ["parent", "lesson", selectedStudentId, id],
    queryFn: () => getChildLesson(selectedStudentId!, id),
    enabled: selectedStudentId !== null,
  });

  if (isPending || !selectedStudentId) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }
  if (isError || !data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-slate-500">수업을 찾을 수 없습니다.</p>
        <Link to="/parent/lessons" className="text-sm text-slate-900 underline">
          목록으로
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Link to="/parent/lessons" className="text-sm text-slate-500 underline">
        ← 수업 목록
      </Link>

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span>{data.lessonDate.replace(/-/g, ".")}</span>
          <span>·</span>
          <span>{data.classRoomName}</span>
        </div>
        <h2 className="mt-1 text-lg font-semibold text-slate-900">
          {data.title ?? "제목 없음"}
        </h2>
        {/* 출결은 캘린더(P-2)와 같은 규칙이다. null은 결석이 아니라 확정 전이다 */}
        <span
          className={`mt-2 inline-block rounded-md px-2 py-1 text-xs ${
            data.attendanceStatus
              ? DAY_STATUS_STYLE[data.attendanceStatus].cell
              : DAY_STATUS_STYLE.PENDING.cell
          }`}
        >
          {data.attendanceStatus
            ? DAY_STATUS_STYLE[data.attendanceStatus].label
            : "출석 미확인"}
        </span>
      </div>

      {data.content && (
        <Section title="수업 내용">
          <p className="whitespace-pre-wrap text-sm text-slate-700">{data.content}</p>
        </Section>
      )}

      {data.keyPoints && (
        <Section title="중점 사항">
          <p className="whitespace-pre-wrap text-sm text-slate-700">{data.keyPoints}</p>
        </Section>
      )}

      {data.nextPreview && (
        <Section title="다음 수업">
          <p className="whitespace-pre-wrap text-sm text-slate-700">{data.nextPreview}</p>
        </Section>
      )}

      {data.homework && (
        <Section title="숙제">
          {/* description은 학부모 응답에서 항상 null이다. 제목·마감·제출 여부까지가 전부다 */}
          <p className="text-sm font-medium text-slate-900">{data.homework.title}</p>
          <div className="mt-2 flex items-center gap-2">
            {/*
              GRID 숙제는 채점 결과로 그린다. submissionStatus로 그리면 ⭕를 받은 학생이
              "미제출"로 뜬다 — ⭕는 온라인 제출을 안 하므로 status가 계속 NOT_SUBMITTED다.
            */}
            {data.homework.kind === "GRID" ? (
              <Badge tone={gradeTone(data.homework.result)}>
                {gradeLabel(
                  data.homework.result,
                  data.homework.completionRate,
                  data.homework.resolvedByResubmission,
                )}
              </Badge>
            ) : (
              <Badge
                tone={
                  data.homework.submissionStatus &&
                  data.homework.submissionStatus !== "NOT_SUBMITTED"
                    ? "ok"
                    : "warn"
                }
              >
                {data.homework.submissionStatus
                  ? SUBMISSION_LABELS[data.homework.submissionStatus]
                  : "미제출"}
              </Badge>
            )}
            {/* GRID는 재제출을 열기 전까지 마감이 없다. 없는 걸 그리면 1970년이 뜬다 */}
            {data.homework.dueAt !== null && (
              <span className="text-xs text-slate-500">
                마감 {formatDueAt(data.homework.dueAt)}
              </span>
            )}
          </div>
        </Section>
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <h3 className="mb-2 text-sm font-semibold text-slate-900">{title}</h3>
      {children}
    </section>
  );
}
