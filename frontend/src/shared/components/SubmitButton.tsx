import type { ButtonHTMLAttributes, ReactNode } from "react";

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  pending?: boolean;
}

export function SubmitButton({ children, pending, disabled, ...props }: Props) {
  return (
    <button
      {...props}
      type={props.type ?? "submit"}
      disabled={disabled || pending}
      className="w-full rounded-xl bg-brand-600 px-4 py-3 text-base font-semibold text-white
                 shadow-card transition-[transform,background-color] hover:bg-brand-700
                 active:scale-[0.99] disabled:bg-slate-300 disabled:shadow-none"
    >
      {pending ? "처리 중…" : children}
    </button>
  );
}
