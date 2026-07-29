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
      className="w-full rounded-lg bg-slate-900 px-4 py-3 text-base font-medium text-white
                 disabled:bg-slate-300"
    >
      {pending ? "처리 중…" : children}
    </button>
  );
}
