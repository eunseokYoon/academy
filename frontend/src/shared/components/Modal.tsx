import { useEffect } from "react";
import type { ReactNode } from "react";
import { Icon } from "./Icon";

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
}

export function Modal({ title, onClose, children, footer }: Props) {
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-brand-950/50 p-0
                 backdrop-blur-[2px] sm:items-center sm:p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-t-3xl bg-white p-5
                   shadow-xl sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        {/*
          닫기는 세 가지 경로가 있다 — ESC, 바깥 클릭, 그리고 이 버튼. 앞의 둘은
          모바일에서 쓸 수 없거나(ESC) 발견되지 않아서(바깥 클릭) 보이는 버튼이 필요하다.
          바텀시트로 뜰 때는 화면을 꽉 채워 "바깥"이 거의 안 보인다.

          아이콘만 있는 버튼이라 aria-label이 유일한 이름이다. 빼면 스크린리더가
          "버튼"이라고만 읽는다.
        */}
        <div className="flex items-start justify-between gap-3">
          <h2 className="min-w-0 text-base font-semibold text-slate-900">{title}</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            // 음수 마진으로 여백을 상쇄한다. 눌리는 범위는 넓히면서 제목과 윗줄은 맞춘다
            className="-mr-2 -mt-2 shrink-0 rounded-lg p-2 text-slate-400 transition-colors
                       hover:bg-slate-100 hover:text-slate-700"
          >
            <Icon name="close" />
          </button>
        </div>
        <div className="mt-4">{children}</div>
        {footer && <div className="mt-6 flex gap-2">{footer}</div>}
      </div>
    </div>
  );
}
