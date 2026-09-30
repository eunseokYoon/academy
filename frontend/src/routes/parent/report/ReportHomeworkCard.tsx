import { gradeLabel, gradeTone } from "../../../shared/homework/grade";
import { SUBMISSION_LABELS } from "../../../shared/homework/types";
import type { ParentLessonDetail } from "../api";

type Homework = NonNullable<ParentLessonDetail["homework"]>;

/** 어느 수업에 딸린 숙제인지 알아야 해서 날짜를 같이 들고 다닌다. */
export interface WeekHomework {
  lessonId: number;
  lessonDate: string;
  homework: Homework;
}

/** 배지 색. gradeTone과 같은 네 가지만 쓴다. */
const TONE: Record<"ok" | "warn" | "danger" | "neutral", string> = {
  ok: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  warn: "bg-amber-50 text-amber-700 ring-amber-200",
  danger: "bg-red-50 text-red-700 ring-red-200",
  neutral: "bg-slate-100 text-slate-500 ring-slate-200",
};

/**
 * 그 주에 나간 숙제와 결과.
 *
 * <p><b>GRID는 result로 그린다.</b> submissionStatus로 그리면 ⭕를 받은 학생이
 * "미제출"로 뜬다 — ⭕는 온라인 제출을 안 하므로 status가 계속 NOT_SUBMITTED다.
 * 이 화면이 학부모 캘린더를 새빨갛게 만들었던 바로 그 자리다.
 *
 * <p>숙제 지시문·사진은 없다. 학부모는 "했는지 여부"까지다.
 */
export function ReportHomeworkCard({ items }: { items: WeekHomework[] }) {
  return (
    <div className="card divide-y divide-slate-100">
      {items.map(({ lessonDate, homework }) => (
        // 키는 숙제 id 다. 수업 하나에 숙제가 여럿이라 lessonId 는 겹친다
        <div key={homework.homeworkId} className="flex items-start justify-between gap-3 p-4">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-brand-900">{homework.title}</p>
            <p className="tnum mt-0.5 text-[11px] text-slate-400">
              {lessonDate.slice(5).replace("-", ".")} 수업
            </p>
          </div>
          <ResultBadge homework={homework} />
        </div>
      ))}
    </div>
  );
}

function ResultBadge({ homework }: { homework: Homework }) {
  if (homework.kind === "GRID") {
    return (
      <Badge tone={gradeTone(homework.result)}>
        {gradeLabel(homework.result, homework.completionRate, homework.resolvedByResubmission)}
      </Badge>
    );
  }
  const submitted =
    homework.submissionStatus !== null && homework.submissionStatus !== "NOT_SUBMITTED";
  return (
    <Badge tone={submitted ? "ok" : "warn"}>
      {homework.submissionStatus ? SUBMISSION_LABELS[homework.submissionStatus] : "미제출"}
    </Badge>
  );
}

function Badge({
  tone,
  children,
}: {
  tone: "ok" | "warn" | "danger" | "neutral";
  children: string;
}) {
  return (
    <span
      className={`shrink-0 whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold
                  ring-1 ring-inset ${TONE[tone]}`}
    >
      {children}
    </span>
  );
}
