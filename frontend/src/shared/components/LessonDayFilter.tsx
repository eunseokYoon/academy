import type { ReactNode } from "react";
import { lessonDayShort, type LessonDayGroup } from "../homework/lessonDay";

const NOW = new Date();
const YEARS = [NOW.getFullYear() - 1, NOW.getFullYear()];
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

/**
 * 숙제 목록 위의 달·수업일 필터. S-2와 P-3가 같이 쓴다.
 *
 * <p><b>달은 서버가 거른다</b>(year·month 파라미터). 프론트에서 거르면 한 페이지(20건)
 * 안에서만 걸러져서 지난 달 숙제가 조용히 사라진다.
 *
 * <p>수업일 칩은 반대로 <b>받아 온 것 안에서</b> 고른다. 그 달 응답의 수업일이 곧 칩 목록이라
 * 별도 API가 필요 없다. 다만 칩은 실제로 그려질 그룹에서 뽑아야 한다 —
 * 다른 배열에서 뽑으면 고른 칩이 빈 화면을 가리킨다.
 */
export function LessonDayFilter<T>({
  year,
  month,
  selectedDay,
  groups,
  onYearChange,
  onMonthChange,
  onDayChange,
}: {
  year: number;
  /** ""이면 전체 월이다. 그때는 달 범위를 안 보내고 서버가 전부 내려준다. */
  month: number | "";
  /** undefined면 전체, null이면 "수업일 없음" 그룹이다. 셋을 구분해야 한다. */
  selectedDay: string | null | undefined;
  groups: LessonDayGroup<T>[];
  onYearChange: (year: number) => void;
  onMonthChange: (month: number | "") => void;
  onDayChange: (day: string | null | undefined) => void;
}) {
  return (
    <div className="space-y-2.5 rounded-2xl bg-white p-3 shadow-card">
      <div className="grid grid-cols-2 gap-2 text-sm">
        <select
          value={year}
          onChange={(e) => onYearChange(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          {YEARS.map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => onMonthChange(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
        >
          <option value="">전체 월</option>
          {MONTHS.map((m) => (
            <option key={m} value={m}>
              {m}월
            </option>
          ))}
        </select>
      </div>

      {/* 수업일이 하나뿐이면 고를 게 없다. 칩 줄이 자리만 차지한다 */}
      {groups.length > 1 && (
        <div className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-0.5">
          <DayChip selected={selectedDay === undefined} onClick={() => onDayChange(undefined)}>
            전체
          </DayChip>
          {groups.map((group) => (
            <DayChip
              key={group.lessonDate ?? "none"}
              selected={selectedDay === group.lessonDate}
              onClick={() => onDayChange(group.lessonDate)}
            >
              {group.lessonDate === null ? "수업일 없음" : lessonDayShort(group.lessonDate)}
            </DayChip>
          ))}
        </div>
      )}
    </div>
  );
}

function DayChip({
  selected,
  onClick,
  children,
}: {
  selected: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`tnum shrink-0 rounded-full px-3 py-1.5 text-[13px] font-semibold
                  transition-colors ${
                    selected
                      ? "bg-brand-600 text-white"
                      : "bg-slate-100 text-slate-600 active:bg-slate-200"
                  }`}
    >
      {children}
    </button>
  );
}
