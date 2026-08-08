import type { HomeworkResult } from "../../../shared/homework/types";

interface Props {
  result: HomeworkResult | null;
  completionRate: number | null;
  resolvedByResubmission: boolean;
  onChange: (result: HomeworkResult | null, completionRate: number | null) => void;
}

const MARKS: { value: HomeworkResult; label: string; tone: string }[] = [
  { value: "DONE", label: "○", tone: "text-emerald-600" },
  { value: "PARTIAL", label: "△", tone: "text-amber-600" },
  { value: "NOT_DONE", label: "✕", tone: "text-rose-600" },
];

/**
 * 그리드 칸 하나. ○ △ ✕ 토글 + △일 때만 퍼센트 입력.
 *
 * <p>같은 표시를 다시 누르면 미채점으로 돌아간다 — 잘못 찍었을 때 되돌릴 길이 필요하다.
 * 퍼센트는 1~99만 받는다. 0과 100은 ✕·○가 이미 표현하고 서버 CHECK가 막는다.
 *
 * <p>여기서 쓰는 색은 Badge의 tone과 무관하다. gradeTone은 배지용이고 이 버튼 세 개는
 * 각자 고정색을 갖는 게 자연스럽다 — 의도적으로 하드코딩했다.
 */
export function GradeCell({ result, completionRate, resolvedByResubmission, onChange }: Props) {
  function toggle(next: HomeworkResult) {
    if (result === next) {
      onChange(null, null);
      return;
    }
    onChange(next, next === "PARTIAL" ? (completionRate ?? 50) : null);
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="flex gap-0.5">
        {MARKS.map((mark) => (
          <button
            key={mark.value}
            type="button"
            onClick={() => toggle(mark.value)}
            aria-pressed={result === mark.value}
            className={`h-7 w-7 rounded text-base leading-none ${
              result === mark.value
                ? `bg-slate-900 text-white`
                : `bg-slate-100 ${mark.tone}`
            }`}
          >
            {mark.label}
          </button>
        ))}
      </div>

      {result === "PARTIAL" && (
        <input
          type="number"
          min={1}
          max={99}
          value={completionRate ?? ""}
          onChange={(e) => {
            const raw = e.target.value;
            onChange("PARTIAL", raw === "" ? null : Number(raw));
          }}
          className="w-14 rounded border border-slate-300 px-1 py-0.5 text-center text-xs"
          aria-label="완료 퍼센트"
        />
      )}

      {resolvedByResubmission && (
        <span className="text-[10px] text-emerald-600">재제출</span>
      )}
    </div>
  );
}
