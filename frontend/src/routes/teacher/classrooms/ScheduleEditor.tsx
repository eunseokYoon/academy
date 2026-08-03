import type { ClassRoomSchedule } from "../api";
import { DAY_LABELS } from "../format";

interface Props {
  value: ClassRoomSchedule[];
  onChange: (next: ClassRoomSchedule[]) => void;
}

const ALL_DAYS = [1, 2, 3, 4, 5, 6, 7];

const FIELD =
  "min-w-0 rounded-xl border border-slate-300 bg-white px-2 py-2.5 text-base outline-none " +
  "transition-colors focus:border-brand-600 focus:ring-4 focus:ring-brand-600/15";

/**
 * 반의 수업 시간 슬롯 편집. 생성 폼과 수정 폼이 같이 쓴다.
 *
 * <p>슬롯 id를 다루지 않는다 — 서버가 요일 기준으로 통째 교체해서 id가 안정적이지 않다.
 * 배열 인덱스로만 추가·삭제한다.
 *
 * <p>요일당 하나다. 이미 쓰인 요일은 선택지에서 뺀다 — 서버도 400으로 막지만
 * 저장한 뒤에 알게 되는 것보다 아예 안 보이는 편이 낫다.
 */
export function ScheduleEditor({ value, onChange }: Props) {
  function update(index: number, patch: Partial<ClassRoomSchedule>) {
    onChange(value.map((slot, i) => (i === index ? { ...slot, ...patch } : slot)));
  }

  function add() {
    const used = new Set(value.map((slot) => slot.dayOfWeek));
    const nextDay = ALL_DAYS.find((day) => !used.has(day));
    if (nextDay === undefined) return;
    onChange([...value, { dayOfWeek: nextDay, startTime: "19:00", endTime: null }]);
  }

  return (
    <div className="space-y-2">
      <span className="block text-sm font-medium text-slate-700">수업 시간</span>

      {value.length === 0 && (
        <p className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-500">
          수업 시간을 넣지 않으면 수업일 일괄 생성을 쓸 수 없습니다.
        </p>
      )}

      {/*
        슬롯 하나가 두 줄이다. 한 줄에 요일·시작·종료·삭제를 다 넣으면 360px에서
        시간 입력이 잘린다 — 한국어 로케일의 input[type=time]은 "오후 07:00"으로
        렌더돼서 폭을 많이 먹는다.
      */}
      {value.map((slot, index) => {
        const usedByOthers = new Set(
          value.filter((_, i) => i !== index).map((other) => other.dayOfWeek),
        );
        return (
          <div key={index} className="space-y-1.5 rounded-xl bg-slate-50 p-2">
            <div className="flex items-center gap-1.5">
              <select
                value={slot.dayOfWeek}
                onChange={(e) => update(index, { dayOfWeek: Number(e.target.value) })}
                aria-label={`${index + 1}번째 수업 요일`}
                className={`w-20 shrink-0 ${FIELD}`}
              >
                {ALL_DAYS.filter((day) => !usedByOthers.has(day)).map((day) => (
                  <option key={day} value={day}>
                    {DAY_LABELS[day]}
                  </option>
                ))}
              </select>
              <span className="flex-1" />
              <button
                type="button"
                onClick={() => onChange(value.filter((_, i) => i !== index))}
                aria-label={`${DAY_LABELS[slot.dayOfWeek]}요일 수업 시간 삭제`}
                className="shrink-0 rounded-lg px-2.5 py-2 text-sm text-slate-400
                           transition-colors hover:text-red-600"
              >
                ✕ 삭제
              </button>
            </div>
            <div className="flex items-center gap-1.5">
              <input
                type="time"
                value={slot.startTime}
                onChange={(e) => update(index, { startTime: e.target.value })}
                required
                aria-label={`${DAY_LABELS[slot.dayOfWeek]}요일 시작 시각`}
                className={`min-w-0 flex-1 ${FIELD}`}
              />
              <span className="shrink-0 text-slate-400">~</span>
              <input
                type="time"
                value={slot.endTime ?? ""}
                onChange={(e) => update(index, { endTime: e.target.value || null })}
                aria-label={`${DAY_LABELS[slot.dayOfWeek]}요일 종료 시각`}
                className={`min-w-0 flex-1 ${FIELD}`}
              />
            </div>
          </div>
        );
      })}

      {value.length < ALL_DAYS.length && (
        <button
          type="button"
          onClick={add}
          className="w-full rounded-xl border border-dashed border-slate-300 py-2 text-sm
                     font-medium text-brand-600 transition-colors hover:bg-brand-50"
        >
          + 수업 시간 추가
        </button>
      )}
    </div>
  );
}

/**
 * 폼 제출 전 검사. 서버도 400으로 막지만, 저장 버튼을 누르고 나서 알게 되면
 * 어디가 틀렸는지 화면에서 짚어주지 못한다.
 *
 * @returns 에러 메시지. 문제가 없으면 null.
 */
export function validateSchedules(schedules: ClassRoomSchedule[]): string | null {
  for (const slot of schedules) {
    if (!slot.startTime) return "시작 시각을 넣어 주세요.";
    // "19:00" 형식이라 사전순 비교가 시각 순서와 일치한다
    if (slot.endTime && slot.endTime <= slot.startTime) {
      return "종료 시각은 시작 시각보다 늦어야 합니다.";
    }
  }
  const days = schedules.map((slot) => slot.dayOfWeek);
  if (new Set(days).size !== days.length) {
    return "같은 요일을 두 번 넣을 수 없습니다.";
  }
  return null;
}

/**
 * 서버가 "19:00" 또는 "19:00:00"을 줄 수 있다. input[type=time]은 초가 붙으면
 * 값을 못 읽으므로 폼 상태로 넣기 전에 잘라 준다.
 */
export function toFormSchedules(schedules: ClassRoomSchedule[]): ClassRoomSchedule[] {
  return schedules.map((slot) => ({
    dayOfWeek: slot.dayOfWeek,
    startTime: slot.startTime.slice(0, 5),
    endTime: slot.endTime ? slot.endTime.slice(0, 5) : null,
  }));
}
