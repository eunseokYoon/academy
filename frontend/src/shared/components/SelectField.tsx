import type { SelectHTMLAttributes } from "react";

interface Props extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  hint?: string;
}

export function SelectField({ label, hint, children, ...props }: Props) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-slate-700">{label}</span>
      <select
        {...props}
        className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base
                   text-slate-900 outline-none focus:border-slate-900 focus:ring-1
                   focus:ring-slate-900"
      >
        {children}
      </select>
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}
