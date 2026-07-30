import { EXAM_TYPE_LABELS } from "../score/types";
import type { StudentExamSchedule } from "../score/types";

/**
 * 시험 D-day. 학생(S-7)과 학부모(P-4)가 같이 쓴다.
 *
 * <p><b>일정이 없으면 영역을 통째로 숨긴다.</b> 0이나 임의 값을 표시하지 마라.
 * 등록이 안 된 반은 D-day가 없는 것이 정상이고, 그건 선생님이 T-11에서 채운다.
 */
export function ExamDdayList({ schedules }: { schedules: StudentExamSchedule[] }) {
  if (schedules.length === 0) return null;

  const upcoming = schedules.filter((s) => s.dDay >= 0);
  const past = schedules.filter((s) => s.dDay < 0);

  return (
    <section className="space-y-2 rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">시험 일정</h3>
      <ul className="divide-y divide-slate-100 text-sm">
        {[...upcoming, ...past].map((schedule) => (
          <li
            key={`${schedule.examType}-${schedule.startDate}`}
            className="flex items-start justify-between gap-3 py-2"
          >
            <div className="min-w-0">
              <p className={schedule.dDay < 0 ? "text-slate-400" : "text-slate-900"}>
                {EXAM_TYPE_LABELS[schedule.examType]}
              </p>
              <p className="text-xs text-slate-500">
                {schedule.startDate.replace(/-/g, ".")} ~{" "}
                {schedule.endDate.slice(5).replace(/-/g, ".")}
              </p>
              {schedule.scopeNote && (
                <p className="mt-0.5 whitespace-pre-wrap text-xs text-slate-500">
                  {schedule.scopeNote}
                </p>
              )}
            </div>
            <span
              className={`shrink-0 text-sm font-semibold ${
                schedule.dDay < 0 ? "text-slate-400" : "text-slate-900"
              }`}
            >
              {schedule.dDay === 0
                ? "오늘"
                : schedule.dDay > 0
                  ? `D-${schedule.dDay}`
                  : `${-schedule.dDay}일 전`}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
