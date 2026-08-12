import type { InputHTMLAttributes } from "react";

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
}

/** 모바일 우선. 입력 글자 크기를 16px 이상으로 둬야 iOS에서 화면이 확대되지 않는다. */
export function TextField({ label, hint, ...props }: Props) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-slate-700">{label}</span>
      <input
        {...props}
        className="mt-1.5 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-base
                   text-slate-900 outline-none transition-colors placeholder:text-slate-400
                   focus:border-brand-600 focus:ring-4 focus:ring-brand-600/15
                   disabled:bg-slate-100"
      />
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}
