import type { ReactNode } from "react";

/**
 * 약관·처리방침 공통 조각. 상호·연락처·보관기간 등 학원이 확정해야 하는 값은
 * 그럴듯하게 지어내지 않고 초안 표시로 남긴다.
 */
export function LegalDraftNotice() {
  return (
    <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
      초안입니다. 상호·연락처·보관 기간 등 최종 문구는 학원에서 확정한 뒤 반영합니다.
    </p>
  );
}

export function LegalSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-6">
      <h2 className="text-sm font-semibold text-slate-900">{title}</h2>
      <p className="mt-1 text-sm leading-relaxed text-slate-600">{children}</p>
    </section>
  );
}

/** 학원이 값을 확정해야 하는 자리. 화면에서 눈에 띄게 남겨 둔다. */
export function Pending({ label }: { label: string }) {
  return <span className="rounded bg-slate-100 px-1 font-mono text-xs text-slate-500">[{label}]</span>;
}
