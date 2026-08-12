import { useState } from "react";

/**
 * 코드·임시 비밀번호는 선생님이 학생에게 옮겨 적어 전달하는 값이다.
 * 복사 버튼이 없으면 손으로 옮기다가 틀린다.
 */
export function CopyButton({ value, label = "복사" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // http(비보안 컨텍스트)에서는 클립보드 API가 막힌다. 선택 영역으로 대체한다
      const area = document.createElement("textarea");
      area.value = value;
      document.body.appendChild(area);
      area.select();
      document.execCommand("copy");
      area.remove();
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <button
      type="button"
      onClick={() => void copy()}
      className="shrink-0 rounded-lg border border-brand-200 bg-brand-50 px-3 py-1.5 text-sm
                 font-medium text-brand-700 transition-colors hover:bg-brand-100"
    >
      {copied ? "복사됨" : label}
    </button>
  );
}
