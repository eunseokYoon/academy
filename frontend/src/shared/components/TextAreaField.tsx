import type { TextareaHTMLAttributes } from "react";

interface Props extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  hint?: string;
}

export function TextAreaField({ label, hint, rows = 4, ...props }: Props) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-slate-700">{label}</span>
      <textarea
        {...props}
        rows={rows}
        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-base
                   text-slate-900 outline-none placeholder:text-slate-400
                   focus:border-slate-900 focus:ring-1 focus:ring-slate-900"
      />
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}
