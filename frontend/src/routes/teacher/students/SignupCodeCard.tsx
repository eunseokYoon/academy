import { CopyButton } from "../../../shared/components/CopyButton";
import { formatPhone } from "../../../shared/lib/phone";
import type { CodeTarget } from "../api";

interface Props {
  target: CodeTarget;
  code: string;
  phone: string;
  expiresAt: string;
}

/**
 * 학생용과 학부모용 코드가 섞이면 서로의 계정으로 가입한다.
 * 누구에게 줄 코드인지 번호와 함께 크게 보여준다.
 */
export function SignupCodeCard({ target, code, phone, expiresAt }: Props) {
  const label = target === "STUDENT" ? "학생용" : "학부모용";
  const message = [
    `[NJW English] 회원가입 안내 (${label})`,
    "아래 주소에서 코드와 본인 전화번호를 입력해 주세요.",
    `${window.location.origin}/signup`,
    `회원가입 코드: ${code}`,
    `전화번호: ${formatPhone(phone)}`,
    "초기 비밀번호는 0000입니다. 로그인 후 꼭 변경해 주세요.",
    `(${expiresAt.slice(0, 10)}까지 가입해 주세요)`,
  ].join("\n");

  return (
    <div className="rounded-xl bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold text-slate-900">{label}</span>
        <span className="text-xs text-slate-400">{expiresAt.slice(0, 10)}까지</span>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <span className="flex-1 font-mono text-2xl tracking-widest text-slate-900">{code}</span>
        <CopyButton value={code} label="코드 복사" />
      </div>
      <p className="mt-1 text-sm text-slate-500">{formatPhone(phone)}</p>
      <div className="mt-3">
        <CopyButton value={message} label="안내 문구 전체 복사" />
      </div>
    </div>
  );
}
