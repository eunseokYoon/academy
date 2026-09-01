import { useMemo } from "react";
import type { ReactNode } from "react";
import type { AttendanceCalendar as CalendarData, DayStatus } from "../attendance/types";
import { DAY_STATUS_STYLE } from "../attendance/types";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 캘린더 칸에 들어가는 일정 하나. 클리닉은 같은 날 둘 이상일 수 있다. */
export interface ClinicEntry {
  date: string;
  /** 확정 전이면 PENDING이다. 호출부에서 null을 PENDING으로 바꿔 넘긴다. */
  status: DayStatus;
}

interface Props {
  data: CalendarData;
  onPrev: () => void;
  onNext: () => void;
  /** 그 달의 내(자녀) 클리닉. 수업과 같은 칩으로 그려진다. */
  clinics?: ClinicEntry[];
}

/**
 * S-6 · P-2 공용 월별 캘린더.
 *
 * <p><b>칸 하나는 날짜지 수업이 아니다.</b> 예전에는 칸 배경 자체가 수업 출석이었는데,
 * 그러면 클리닉만 있는 날은 색을 입힐 곳이 없어 점 같은 별도 표시로 밀려난다. 지금은
 * 칸을 비워 두고 그 안에 일정마다 칩을 넣는다 — 수업도 클리닉도 같은 색·같은 기호다.
 *
 * <p>일정이 없는 날은 아무 칩도 없다. homeworkRate가 null이면 하단 색띠를 그리지 않는다 —
 * Phase 5 전이거나 그날 숙제가 없던 날이다.
 */
export function AttendanceCalendar({ data, onPrev, onNext, clinics = [] }: Props) {
  const byDate = useMemo(() => {
    const map = new Map<string, (typeof data.days)[number]>();
    for (const day of data.days) map.set(day.date, day);
    return map;
  }, [data.days]);

  // 같은 날 클리닉이 둘일 수 있어 날짜당 배열로 모은다
  const clinicsByDate = useMemo(() => {
    const map = new Map<string, ClinicEntry[]>();
    for (const clinic of clinics) {
      const bucket = map.get(clinic.date);
      if (bucket) bucket.push(clinic);
      else map.set(clinic.date, [clinic]);
    }
    return map;
  }, [clinics]);

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
                lesson={entry?.status}
                clinics={clinicsByDate.get(key) ?? []}
                homeworkRate={entry?.homeworkRate ?? null}
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
    // 대체 등원은 출석에 합친다(2026-09-01 확정). 4칸을 5칸으로 늘리면 360px에서 뭉개지고,
    // 학부모가 여기서 보는 건 "몇 번 빠졌나"라 온 날은 한 칸이면 된다.
    // 어느 날이 대체 등원이었는지는 캘린더 칸이 라벨로 그대로 보여준다
    {
      label: "출석",
      value: data.summary.present + data.summary.makeup,
      color: "text-emerald-600",
    },
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

/**
 * 일정 한 줄. <b>수업과 클리닉이 같은 모양을 쓴다</b> — 색은 출결 상태고,
 * 글씨가 종류와 상태를 그대로 말한다.
 *
 * <p>축약하지 않는다. 좁은 화면에서는 "수업 / 출석" 두 줄로 접히고 넓어지면 한 줄이 된다.
 * <b>break-keep이 빠지면 안 된다</b> — 브라우저는 한글을 아무 데서나 끊어서
 * "수업 출" / "석"처럼 단어 중간이 잘린다. 띄어쓰기에서만 접히게 하는 게 이 클래스다.
 *
 * <p>종류를 색이나 모양으로 나누지 않는 이유는 상태에 색을 이미 다 쓰고 있어서다.
 * 종류까지 색으로 나누면 빨강이 "결석"인지 "클리닉"인지 알 수 없게 된다.
 */
/**
 * 칸 안의 일정 하나. <b>확정 전에는 종류만 적는다.</b>
 *
 * <p>"수업 미확인"은 무언가 잘못된 것처럼 읽히는데 실제로는 아직 오지 않은 날이거나
 * 선생님이 아직 안 누른 날일 뿐이다. 달력 대부분이 미래라 이 글자가 화면을 덮었다.
 * 회색이 이미 "확정 전"을 말하고 범례가 그 색을 풀어 준다.
 *
 * <p>다만 색은 눈으로만 읽힌다. 읽어 주는 기계에는 상태를 그대로 들려준다(sr-only).
 */
function SessionChip({ kind, status }: { kind: "수업" | "클리닉"; status: DayStatus }) {
  const style = DAY_STATUS_STYLE[status];
  return (
    <span
      className={`block break-keep rounded px-0.5 py-1 text-center text-[10px] leading-tight
                  ring-1 ring-inset ${style.cell}`}
    >
      {status === "PENDING" ? (
        <>
          {kind}
          <span className="sr-only"> {style.label}</span>
        </>
      ) : (
        `${kind} ${style.label}`
      )}
    </span>
  );
}

function DayCell({
  day,
  lesson,
  clinics,
  homeworkRate,
}: {
  day: number;
  lesson?: DayStatus;
  clinics: ClinicEntry[];
  homeworkRate: number | null;
}) {
  const empty = lesson === undefined && clinics.length === 0;

  return (
    /*
      칸 바탕은 비운다. 일정 있는 날에 옅은 회색을 깔아 봤더니 칩 색과 겹쳐 격자가
      얼룩덜룩해지기만 했다 — 그 날에 뭐가 있었는지는 칩이 이미 말한다.
      바탕에 상태 색을 쓰는 건 더 안 된다. 칸 하나에 일정이 둘이면 어느 쪽 색인지 알 수 없다.

      높이는 일정이 있는 칸에만 준다. 칩 두 줄이 들어가는 값이라 그 주는 줄이 고르고,
      일정이 아예 없는 주(달 첫 줄·마지막 줄이 자주 그렇다)는 숫자 높이로 접힌다.
      모든 칸에 주면 빈 주가 70px씩 차지해서 달력 위아래가 텅 빈 채로 늘어진다.
      한 줄 안에서는 그리드가 알아서 높이를 맞춘다.
    */
    <div className={`flex flex-col rounded-lg p-1 ${empty ? "" : "min-h-[4.25rem]"}`}>
      <span
        className={`tnum text-center text-xs ${
          empty ? "text-slate-300" : "font-semibold text-slate-700"
        }`}
      >
        {day}
      </span>

      {/* 수업이 먼저, 클리닉이 아래다. 그 날 순서가 아니라 고정 순서라 눈이 익는다 */}
      <div className="mt-0.5 space-y-0.5">
        {lesson !== undefined && <SessionChip kind="수업" status={lesson} />}
        {clinics.map((clinic, index) => (
          <SessionChip key={index} kind="클리닉" status={clinic.status} />
        ))}
      </div>

      {/*
        null이면 띠를 그리지 않는다. 0은 빨강이라 "숙제 없던 날"과 구분돼야 한다.
        안 채워진 쪽을 회색으로 바꾼 이유는 칸 배경이 흰색이 됐기 때문이다 —
        예전처럼 반투명 흰색으로 두면 띠가 통째로 안 보인다.
      */}
      {homeworkRate !== null && (
        <span
          // 칸 바닥이 아니라 칩 바로 아래다. 바닥에 붙이면 일정이 하나뿐인 날에
          // 칩과 띠 사이가 벌어져 어느 날 것인지 모를 표시처럼 떠 보인다
          className="mt-1 block h-1 w-full rounded-full"
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

/**
 * 칸 안에 상태가 글씨로 적혀 있으니 범례는 색만 짚어 준다.
 * 예전에는 "출·지·결" 같은 한 글자 기호를 풀어 주는 역할이었는데, 그 기호가 없어졌다.
 */
function Legend() {
  return (
    <ul className="flex flex-wrap gap-x-3 gap-y-1.5 px-1 text-xs text-slate-500">
      {(Object.keys(DAY_STATUS_STYLE) as DayStatus[]).map((status) => {
        const style = DAY_STATUS_STYLE[status];
        return (
          <li key={status} className="flex items-center gap-1">
            <span className={`inline-block h-3 w-3 rounded ring-1 ring-inset ${style.cell}`} />
            {style.label}
          </li>
        );
      })}
    </ul>
  );
}
