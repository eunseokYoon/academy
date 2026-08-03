import type { ReactNode } from "react";

type Tone = "neutral" | "warn" | "danger" | "ok" | "brand";

/**
 * 옅은 배경 + 같은 계열 테두리. 테두리가 없으면 흰 카드 위에서 배지가 번져 보인다.
 *
 * 상태색은 브랜드 남색으로 바꾸지 마라. 포인트가 파랑으로 옮겨간 덕분에
 * 빨강이 "결석·위험" 한 가지 뜻만 갖게 됐다 — 그 이득을 다시 버리는 셈이 된다.
 */
const TONES: Record<Tone, string> = {
  neutral: "bg-slate-100 text-slate-600 ring-slate-200",
  ok: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  // 선생님 화면은 "무엇을 안 했는지"를 찾는 곳이라 미완료가 눈에 띄어야 한다
  warn: "bg-amber-50 text-amber-700 ring-amber-200",
  danger: "bg-red-50 text-red-700 ring-red-200",
  brand: "bg-brand-50 text-brand-700 ring-brand-200",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`tnum inline-flex items-center rounded-md px-1.5 py-0.5 text-xs font-medium
                  ring-1 ring-inset ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
