import { SectionHead, TintBlock } from "./Section";
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
    <section>
      <SectionHead tone="brand" title="시험 일정" />
      <TintBlock tone="brand">
        {[...upcoming, ...past].map((schedule) => {
          // 지난 시험은 흐리게 남긴다. 지우면 "범위가 뭐였더라"를 확인할 데가 없어진다
          const over = schedule.dDay < 0;
          return (
            <div
              key={`${schedule.examType}-${schedule.startDate}`}
              className="flex items-start justify-between gap-3 px-3.5 py-3"
            >
              <div className="min-w-0">
                <p
                  className={`text-[14px] font-bold ${
                    over ? "text-brand-900/40" : "text-brand-900"
                  }`}
                >
                  {EXAM_TYPE_LABELS[schedule.examType]}
                </p>
                <p className="tnum mt-0.5 text-[11.5px] text-brand-600/70">
                  {schedule.startDate.replace(/-/g, ".")} ~{" "}
                  {schedule.endDate.slice(5).replace(/-/g, ".")}
                </p>
                {schedule.scopeNote && (
                  <p className="mt-1 whitespace-pre-wrap text-[12px] leading-relaxed
                                text-brand-950/70">
                    {schedule.scopeNote}
                  </p>
                )}
              </div>
              <span
                className={`tnum shrink-0 text-[17px] font-extrabold tracking-[-0.03em] ${
                  over ? "text-brand-900/35" : "text-brand-900"
                }`}
              >
                {schedule.dDay === 0
                  ? "오늘"
                  : schedule.dDay > 0
                    ? `D-${schedule.dDay}`
                    : `${-schedule.dDay}일 전`}
              </span>
            </div>
          );
        })}
      </TintBlock>
    </section>
  );
}
