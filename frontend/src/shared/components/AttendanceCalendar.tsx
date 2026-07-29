import { useMemo } from "react";
import type { ReactNode } from "react";
import type { AttendanceCalendar as CalendarData, DayStatus } from "../attendance/types";
import { DAY_STATUS_STYLE } from "../attendance/types";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

interface Props {
  data: CalendarData;
  onPrev: () => void;
  onNext: () => void;
  /** 클리닉처럼 날짜 칸에 얹을 표시. P-2가 쓴다. */
  dayBadge?: (date: string) => ReactNode;
}

/**
 * S-6 · P-2 공용 월별 캘린더.
 *
 * <p>수업이 있는 날만 data.days에 담겨 온다. 나머지는 빈 칸이다.
 * homeworkRate가 null이면 하단 색띠를 그리지 않는다 — Phase 5 전이거나 그날 숙제가 없던 날이다.
 */
export function AttendanceCalendar({ data, onPrev, onNext, dayBadge }: Props) {
  const byDate = useMemo(() => {
    const map = new Map<string, (typeof data.days)[number]>();
    for (const day of data.days) map.set(day.date, day);
    return map;
  }, [data.days]);

  // 1일이 무슨 요일인지에 따라 앞을 비운다
  const first = new Date(data.year, data.month - 1, 1);
  const daysInMonth = new Date(data.year, data.month, 0).getDate();
  const leading = first.getDay();

  const cells: (number | null)[] = [
    ...Array.from({ length: leading }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];

  function dateKey(day: number): string {
    return `${data.year}-${String(data.month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <button
          type="button"
          onClick={onPrev}
          aria-label="이전 달"
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-600"
        >
          ‹ 이전
        </button>
        <span className="text-sm font-semibold text-slate-900">
          {data.year}년 {data.month}월
        </span>
        <button
          type="button"
          onClick={onNext}
          aria-label="다음 달"
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm text-slate-600"
        >
          다음 ›
        </button>
      </div>

      <SummaryBar data={data} />

      <div className="rounded-xl bg-white p-2 shadow-sm">
        <div className="grid grid-cols-7 text-center text-xs text-slate-400">
          {WEEKDAYS.map((label) => (
            <div key={label} className="py-1">
              {label}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {cells.map((day, index) => {
            if (day === null) return <div key={`blank-${index}`} />;
            const key = dateKey(day);
            const entry = byDate.get(key);
            return (
              <DayCell
                key={key}
                day={day}
                status={entry?.status}
                homeworkRate={entry?.homeworkRate ?? null}
                badge={dayBadge?.(key)}
              />
            );
          })}
        </div>
      </div>

      <Legend />
    </div>
  );
}

function SummaryBar({ data }: { data: CalendarData }) {
  const items = [
    { label: "출석", value: data.summary.present },
    { label: "지각", value: data.summary.late },
    { label: "결석", value: data.summary.absent },
    { label: "병·공결", value: data.summary.sick + data.summary.excused },
  ];
  return (
    <div className="flex items-center justify-between rounded-xl bg-white p-3 text-sm shadow-sm">
      <div className="flex gap-3">
        {items.map((item) => (
          <span key={item.label} className="text-slate-600">
            {item.label} <b className="text-slate-900">{item.value}</b>
          </span>
        ))}
      </div>
      {/* Phase 5 전에는 null이라 아예 숨긴다. 0%로 보이면 오해를 부른다 */}
      {data.homeworkCompletionRate !== null && (
        <span className="text-slate-600">
          숙제 <b className="text-slate-900">{data.homeworkCompletionRate}%</b>
        </span>
      )}
    </div>
  );
}

function DayCell({
  day,
  status,
  homeworkRate,
  badge,
}: {
  day: number;
  status?: DayStatus;
  homeworkRate: number | null;
  badge?: ReactNode;
}) {
  // 수업이 없는 날은 배열에 없다. 빈 칸으로 둔다
  if (!status) {
    return (
      <div className="flex aspect-square flex-col items-center justify-start rounded-lg p-1">
        <span className="text-xs text-slate-300">{day}</span>
        {badge}
      </div>
    );
  }

  const style = DAY_STATUS_STYLE[status];
  return (
    <div
      className={`flex aspect-square flex-col items-center justify-start rounded-lg p-1
                  ${style.cell}`}
      title={`${day}일 ${style.label}`}
    >
      <span className="text-xs font-medium">{day}</span>
      <span className="text-[10px] leading-tight">{style.mark}</span>
      {badge}
      {/* null이면 띠를 그리지 않는다. 0은 빨강이라 "숙제 없던 날"과 구분돼야 한다 */}
      {homeworkRate !== null && (
        <span
          className="mt-auto block h-1 w-full rounded-full"
          style={{
            background: `linear-gradient(90deg,
              ${homeworkRate >= 50 ? "#10b981" : "#ef4444"} ${homeworkRate}%,
              #e2e8f0 ${homeworkRate}%)`,
          }}
          aria-label={`숙제 ${homeworkRate}%`}
        />
      )}
    </div>
  );
}

/** 색상만으로 구분하면 색약 사용자가 읽을 수 없다. 범례는 선택이 아니다. */
function Legend() {
  return (
    <ul className="flex flex-wrap gap-2 text-xs text-slate-600">
      {(Object.keys(DAY_STATUS_STYLE) as DayStatus[]).map((status) => {
        const style = DAY_STATUS_STYLE[status];
        return (
          <li key={status} className="flex items-center gap-1">
            <span
              className={`inline-flex h-4 w-4 items-center justify-center rounded
                          text-[10px] ${style.cell}`}
            >
              {style.mark}
            </span>
            {style.label}
          </li>
        );
      })}
    </ul>
  );
}
