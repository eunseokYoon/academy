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
      <SummaryGrid data={data} />
      <HomeworkRate rate={data.homeworkCompletionRate} />

      <div className="card p-3">
        <div className="flex items-center justify-between px-1 pb-2">
          <MonthButton onClick={onPrev} label="이전 달">
            ‹
          </MonthButton>
          <span className="tnum text-sm font-bold text-brand-900">
            {data.year}년 {data.month}월
          </span>
          <MonthButton onClick={onNext} label="다음 달">
            ›
          </MonthButton>
        </div>

        {/* 일요일 빨강 · 토요일 파랑. 한국 달력 관습이라 없으면 어색하다 */}
        <div className="grid grid-cols-7 text-center text-xs font-medium">
          {WEEKDAYS.map((label, index) => (
            <div
              key={label}
              className={`py-1.5 ${
                index === 0 ? "text-red-400" : index === 6 ? "text-brand-400" : "text-slate-400"
              }`}
            >
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

function MonthButton({
  onClick,
  label,
  children,
}: {
  onClick: () => void;
  label: string;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className="grid h-8 w-8 place-items-center rounded-lg text-lg leading-none text-brand-600
                 transition-colors hover:bg-brand-50"
    >
      {children}
    </button>
  );
}

/**
 * 참고 디자인의 4칸 통계 줄. 숫자를 크게, 라벨을 작게 둔다 —
 * 학부모가 이 화면에서 제일 먼저 보는 건 "몇 번 빠졌나"다.
 */
function SummaryGrid({ data }: { data: CalendarData }) {
  const items = [
    { label: "출석", value: data.summary.present, color: "text-emerald-600" },
    { label: "지각", value: data.summary.late, color: "text-amber-600" },
    { label: "결석", value: data.summary.absent, color: "text-red-600" },
    { label: "병·공결", value: data.summary.sick + data.summary.excused, color: "text-sky-600" },
  ];
  return (
    <div className="grid grid-cols-4 gap-2">
      {items.map((item) => (
        <div key={item.label} className="card px-2 py-3 text-center">
          {/*
            여기는 tnum을 쓰지 않는다. 고정폭 숫자는 "11"을 "1 1"처럼 벌려 놓는다.
            자릿수를 세로로 맞출 표도 아니고 칸 너비도 고정이라 이득이 없다.
          */}
          <p className={`text-2xl font-extrabold leading-none ${item.color}`}>{item.value}</p>
          <p className="mt-1.5 text-[11px] text-slate-500">{item.label}</p>
        </div>
      ))}
    </div>
  );
}

/** Phase 5 전에는 null이라 아예 숨긴다. 0%로 보이면 오해를 부른다. */
function HomeworkRate({ rate }: { rate: number | null }) {
  if (rate === null) return null;
  return (
    <div className="card flex items-center justify-between px-4 py-3">
      <span className="text-sm text-slate-600">이번 달 숙제 완료율</span>
      <span className="tnum text-sm font-bold text-brand-700">{rate}%</span>
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
        <span className="tnum text-xs text-slate-300">{day}</span>
        {badge}
      </div>
    );
  }

  const style = DAY_STATUS_STYLE[status];
  return (
    <div
      className={`flex aspect-square flex-col items-center justify-start rounded-lg p-1
                  ring-1 ring-inset ${style.cell}`}
      title={`${day}일 ${style.label}`}
    >
      <span className="tnum text-xs font-semibold">{day}</span>
      <span className="text-[10px] leading-tight">{style.mark}</span>
      {badge}
      {/* null이면 띠를 그리지 않는다. 0은 빨강이라 "숙제 없던 날"과 구분돼야 한다 */}
      {homeworkRate !== null && (
        <span
          className="mt-auto block h-1 w-full rounded-full"
          style={{
            background: `linear-gradient(90deg,
              ${homeworkRate >= 50 ? "#10b981" : "#ef4444"} ${homeworkRate}%,
              rgba(255,255,255,0.55) ${homeworkRate}%)`,
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
    <ul className="flex flex-wrap gap-x-3 gap-y-1.5 px-1 text-xs text-slate-500">
      {(Object.keys(DAY_STATUS_STYLE) as DayStatus[]).map((status) => {
        const style = DAY_STATUS_STYLE[status];
        return (
          <li key={status} className="flex items-center gap-1">
            <span
              className={`inline-flex h-4 w-4 items-center justify-center rounded text-[10px]
                          ring-1 ring-inset ${style.cell}`}
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
