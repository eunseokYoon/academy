import type { ReactNode } from "react";

type Tone = "neutral" | "warn" | "danger" | "ok";

const TONES: Record<Tone, string> = {
  neutral: "bg-slate-100 text-slate-600",
  ok: "bg-emerald-50 text-emerald-700",
  // 선생님 화면은 "무엇을 안 했는지"를 찾는 곳이라 미완료가 눈에 띄어야 한다
  warn: "bg-amber-50 text-amber-700",
  danger: "bg-red-50 text-red-700",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-md px-1.5 py-0.5 text-xs
                  font-medium ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
