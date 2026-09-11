import type { ReactNode } from "react";
import { DAY_STATUS_STYLE } from "../../../shared/attendance/types";
import { dayLabel } from "../../../shared/date";
import type { ParentLessonDetail } from "../api";

/**
 * 그 주 수업 한 번. 날짜·제목·출결이 머리고, 그 아래가 선생님이 쓴 글이다.
 *
 * <p><b>영상은 없다.</b> 응답의 videoId·embedUrl은 학부모에게 언제나 null이다 —
 * "영상이 없다"가 아니라 "학부모는 못 본다"는 뜻이라 자리를 만들지도 마라.
 *
 * <p>세 칸(수업 내용·중점 사항·다음 수업)은 각각 비어 있을 수 있다.
 * 없는 칸은 통째로 숨긴다 — 빈 제목만 남으면 선생님이 안 쓴 것인지
 * 화면이 깨진 것인지 구분되지 않는다.
 */
export function ReportLessonCard({ lesson }: { lesson: ParentLessonDetail }) {
  const status = lesson.attendanceStatus ?? "PENDING";

  return (
    <article className="card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="tnum eyebrow">
            {lesson.lessonDate.slice(5).replace("-", ".")} ({dayLabel(lesson.lessonDate)})
          </p>
          <h4 className="mt-1 text-base font-bold tracking-[-0.01em] text-brand-900">
            {lesson.title ?? "제목 없음"}
          </h4>
        </div>
        {/* 출결은 캘린더(P-2)와 같은 규칙·같은 색이다. null은 결석이 아니라 확정 전이다 */}
        <span
          className={`shrink-0 rounded-md px-2 py-1 text-xs font-medium ${
            DAY_STATUS_STYLE[status].cell
          }`}
        >
          {lesson.attendanceStatus ? DAY_STATUS_STYLE[status].label : "출석 미확인"}
        </span>
      </div>

      {(lesson.content || lesson.keyPoints || lesson.homeworkNote || lesson.clinicNote) && (
        <div
          className="mt-3 space-y-3 divide-y divide-slate-100 border-t border-slate-100 pt-3
                     [&>*+*]:pt-3"
        >
          {lesson.content && <Block label="수업 내용" text={lesson.content} />}
          {/* 중점 사항만 배경을 깐다. 선생님이 "이건 꼭 보세요"로 쓰는 칸이다 */}
          {lesson.keyPoints && (
            <Block label="중점 사항" text={lesson.keyPoints} tone="highlight" />
          )}
          {lesson.homeworkNote && <Block label="수업 숙제" text={lesson.homeworkNote} />}
          {lesson.clinicNote && <Block label="클리닉" text={lesson.clinicNote} />}
        </div>
      )}
    </article>
  );
}

function Block({
  label,
  text,
  tone = "plain",
}: {
  label: string;
  text: string;
  tone?: "plain" | "highlight";
}) {
  const wrapper =
    tone === "highlight" ? "rounded-xl border-l-[3px] border-accent-500 bg-accent-50 p-3" : "";
  return (
    <div className={wrapper}>
      {/*
        11px 회색(.eyebrow)에서 올렸다(2026-09-10). 항목이 넷이 되면서 제목이 본문보다
        약해 네 덩어리가 하나로 읽혔다. 본문이 14px이므로 제목은 그보다 굵어야 한다.
        중점 사항만 주황을 유지한다 — 선생님이 "이건 꼭 보세요"로 쓰는 칸이다.
      */}
      <p
        className={`text-[13px] font-bold tracking-[-0.01em] ${
          tone === "highlight" ? "text-accent-700" : "text-brand-900"
        }`}
      >
        {label}
      </p>
      {/* 줄바꿈은 선생님이 쓴 그대로 살린다 */}
      <p className="mt-1 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{text}</p>
    </div>
  );
}

/** 카드가 하나도 없는 주. 빈 화면 대신 왜 비었는지를 적는다. */
export function ReportEmpty({ children }: { children: ReactNode }) {
  return <p className="card p-6 text-center text-sm text-slate-500">{children}</p>;
}
