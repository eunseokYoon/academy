import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { STATUS_LABEL } from "../../shared/attendance/types";
import { Badge } from "../../shared/components/Badge";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { SUBMISSION_LABELS, formatDueAt } from "../../shared/homework/types";
import { getMyLesson, recordLessonView } from "./api";

/** 30초 간격이면 충분하다. 매초 호출하면 200명 기준으로 불필요한 부하가 생긴다. */
const FLUSH_INTERVAL_MS = 30_000;

/**
 * 시청 시간을 서버에 누적 보고한다.
 *
 * <p>YouTube IFrame Player API로 정밀 구간을 추적하지 않는다 — 복잡도가 급증하고
 * 필요한 것은 "봤는지 여부와 대략적 누적 시간"뿐이다.
 *
 * <p>이탈은 visibilitychange로 잡는다. beforeunload는 모바일에서 신뢰할 수 없다.
 */
function useViewTracker(lessonId: number, playing: boolean) {
  const lastFlushRef = useRef<number | null>(null);

  useEffect(() => {
    if (!playing) return;

    // 재생 시작 시 0을 보내 시청 기록 행을 먼저 만든다
    lastFlushRef.current = Date.now();
    void recordLessonView(lessonId, 0).catch(() => undefined);

    // 경과한 실제 시간만 더한다. 고정값 30을 더하면 탭을 열어만 둔 시간이 부풀려진다
    const flush = () => {
      const previous = lastFlushRef.current;
      if (previous == null) return;
      const seconds = Math.round((Date.now() - previous) / 1000);
      lastFlushRef.current = Date.now();
      if (seconds > 0) void recordLessonView(lessonId, seconds).catch(() => undefined);
    };

    const timer = window.setInterval(flush, FLUSH_INTERVAL_MS);
    const onVisibility = () => {
      if (document.visibilityState === "hidden") flush();
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibility);
      flush();
    };
  }, [lessonId, playing]);
}

/** S-5 상세. 학생 전용 화면이다. */
export default function StudentLessonDetailPage() {
  const { lessonId } = useParams();
  const id = Number(lessonId);
  const [playing, setPlaying] = useState(false);

  const { data, isPending, isError } = useQuery({
    queryKey: ["student", "lesson", id],
    queryFn: () => getMyLesson(id),
  });

  useViewTracker(id, playing);

  if (isPending) return <p className="text-sm text-slate-400">불러오는 중…</p>;
  if (isError || !data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-slate-500">수업을 찾을 수 없습니다.</p>
        <Link to="/student/lessons" className="text-sm text-slate-900 underline">
          목록으로
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Link to="/student/lessons" className="text-sm text-slate-500 underline">
        ← 수업 목록
      </Link>

      <div className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span>{data.lessonDate.replace(/-/g, ".")}</span>
          <span>·</span>
          <span>{data.classRoomName}</span>
          {data.attendanceStatus && (
            <Badge tone={data.attendanceStatus === "PRESENT" ? "ok" : "warn"}>
              {STATUS_LABEL[data.attendanceStatus]}
            </Badge>
          )}
        </div>
        <h2 className="mt-1 text-lg font-semibold text-slate-900">
          {data.title ?? "제목 없음"}
        </h2>
      </div>

      {/* videoId가 null이면 영상이 등록되지 않은 수업이다. 영역을 통째로 숨긴다 */}
      {data.embedUrl && (
        <section className="space-y-2">
          {playing ? (
            <div className="aspect-video w-full overflow-hidden rounded-xl bg-black">
              <iframe
                src={`${data.embedUrl}?rel=0&modestbranding=1&autoplay=1`}
                title="수업 영상"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media;
                       picture-in-picture"
                allowFullScreen
                className="h-full w-full"
              />
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setPlaying(true)}
              className="w-full rounded-xl bg-slate-900 px-4 py-4 text-sm font-medium text-white"
            >
              ▶ 수업영상 시청하기
            </button>
          )}
        </section>
      )}

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
          <Link
            to={`/student/homeworks/${data.homework.homeworkId}`}
            className="block space-y-1"
          >
            <p className="text-sm font-medium text-slate-900">{data.homework.title}</p>
            {data.homework.description && (
              <p className="whitespace-pre-wrap text-sm text-slate-600">
                {data.homework.description}
              </p>
            )}
            <div className="flex items-center gap-2 pt-1">
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
              ) : data.homework.submissionStatus ? (
                <Badge
                  tone={data.homework.submissionStatus === "NOT_SUBMITTED" ? "warn" : "ok"}
                >
                  {SUBMISSION_LABELS[data.homework.submissionStatus]}
                </Badge>
              ) : (
                <Badge tone="warn">미제출</Badge>
              )}
              {/* GRID는 재제출을 열기 전까지 마감이 없다. 없는 걸 그리면 1970년이 뜬다 */}
              {data.homework.dueAt !== null && (
                <span className="text-xs text-slate-500">
                  마감 {formatDueAt(data.homework.dueAt)}
                </span>
              )}
            </div>
          </Link>
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
