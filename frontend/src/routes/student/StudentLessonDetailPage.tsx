import { useRef, useState } from "react";
import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { STATUS_LABEL } from "../../shared/attendance/types";
import { Badge } from "../../shared/components/Badge";
import { BackLink } from "../../shared/components/Section";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { SUBMISSION_LABELS, formatDueAt } from "../../shared/homework/types";
import { getMyLesson, reportLessonWatch } from "./api";
import { useYoutubeWatch, watchParams } from "../../shared/lesson/youtubeWatch";

/** S-5 상세. 학생 전용 화면이다. */
export default function StudentLessonDetailPage() {
  const navigate = useNavigate();
  const { lessonId } = useParams();
  const id = Number(lessonId);
  /** 재생 중인 영상의 인덱스. null이면 아직 안 눌렀다 — 처음부터 iframe을 심지 않는다 */
  const [playing, setPlaying] = useState<number | null>(null);
  const playerRef = useRef<HTMLIFrameElement>(null);

  const { data, isPending, isError } = useQuery({
    queryKey: ["student", "lesson", id],
    queryFn: () => getMyLesson(id),
  });

  // 시청 기록(2026-09-29). 결석인 학생이 80% 이상 보면 서버가 출결을 온라인으로 바꾼다.
  // 훅은 조기 반환보다 위에 있어야 한다 — 재생 중인 영상이 없으면 아무것도 안 한다
  const playingUrl = playing === null ? null : data?.videos[playing]?.embedUrl ?? null;
  useYoutubeWatch(playerRef, playingUrl, (report) => reportLessonWatch(id, report));

  if (isPending) return <p className="text-sm text-slate-400">불러오는 중…</p>;
  if (isError || !data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-slate-500">수업을 찾을 수 없습니다.</p>
        <Link to="/student/lessons" className="text-sm text-brand-900 underline">
          목록으로
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <BackLink onClick={() => navigate("/student/lessons")}>수업 목록</BackLink>

      <div className="rounded-2xl bg-white p-4 shadow-card">
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
        <h2 className="mt-1 text-lg font-semibold text-brand-900">
          {data.title ?? "제목 없음"}
        </h2>
      </div>

      {/*
        영상은 여러 개다(2026-09-04). 재생목록 임베드가 「일부 공개」 목록에서 재생되지
        않아 학생이 못 보던 것을 이 구조가 비켜 간다 — 영상 단독 임베드는 공개 설정에
        걸리지 않는다.

        비어 있으면 영역을 통째로 숨긴다. 처음부터 iframe을 심지 않는 것도 그대로다 —
        여러 개면 그만큼 무거워진다.
      */}
      {data.videos.length > 0 && (
        <section className="space-y-2">
          {playing === null ? (
            <button
              type="button"
              onClick={() => setPlaying(0)}
              className="w-full rounded-xl bg-brand-900 px-4 py-4 text-sm font-medium text-white"
            >
              ▶ 수업영상 시청하기
              {data.videos.length > 1 && ` (${data.videos.length}개)`}
            </button>
          ) : (
            <div className="aspect-video w-full overflow-hidden rounded-xl bg-black">
              <iframe
                // 영상을 바꾸면 새 플레이어다 — 시청 기록이 영상마다 따로 붙는다
                key={playing}
                ref={playerRef}
                src={`${data.videos[playing].embedUrl}${
                  data.videos[playing].embedUrl?.includes("?") ? "&" : "?"
                }rel=0&modestbranding=1&autoplay=1&${watchParams()}`}
                title={data.videos[playing].title ?? `수업 영상 ${playing + 1}`}
                allow="accelerometer; autoplay; clipboard-write; encrypted-media;
                       picture-in-picture"
                allowFullScreen
                className="h-full w-full"
              />
            </div>
          )}

          {/* 영상이 하나뿐이면 목록을 그리지 않는다. 고를 것이 없다 */}
          {data.videos.length > 1 && (
            <ul className="space-y-1.5">
              {data.videos.map((video, i) => (
                <li key={i}>
                  <button
                    type="button"
                    onClick={() => setPlaying(i)}
                    className={`flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left
                                text-sm transition-colors ${
                                  playing === i
                                    ? "bg-brand-900 text-white"
                                    : "bg-white text-brand-900 shadow-card active:bg-brand-50"
                                }`}
                  >
                    <span
                      className={`shrink-0 text-xs font-bold ${
                        playing === i ? "text-white/70" : "text-brand-400"
                      }`}
                    >
                      {i + 1}
                    </span>
                    {/* 이름을 안 적으면 "영상 N"으로 채운다 — 선생님이 매번 짓지 않아도 된다 */}
                    <span className="min-w-0 flex-1 truncate">
                      {video.title ?? `영상 ${i + 1}`}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
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

      {data.homeworkNote && (
        <Section title="수업 숙제">
          <p className="whitespace-pre-wrap text-sm text-slate-700">{data.homeworkNote}</p>
        </Section>
      )}

      {data.clinicNote && (
        <Section title="클리닉">
          <p className="whitespace-pre-wrap text-sm text-slate-700">{data.clinicNote}</p>
        </Section>
      )}

      {data.homework && (
        <Section title="숙제">
          <Link
            to={`/student/homeworks/${data.homework.homeworkId}`}
            className="block space-y-1"
          >
            <p className="text-sm font-medium text-brand-900">{data.homework.title}</p>
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
    <section className="rounded-2xl bg-white p-4 shadow-card">
      <h3 className="mb-2 text-sm font-semibold text-brand-900">{title}</h3>
      {children}
    </section>
  );
}
