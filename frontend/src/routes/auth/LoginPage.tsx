import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { errorMessage } from "../../shared/api/errors";
import { homePathOf, useAuth } from "../../shared/auth/AuthContext";
import { ACADEMY_NAME } from "../../shared/branding";
import { FullScreenLoader } from "../../shared/components/FullScreenLoader";
import { FormError } from "../../shared/components/FormError";
import { SubmitButton } from "../../shared/components/SubmitButton";
import { TextField } from "../../shared/components/TextField";
import { formatPhone } from "../../shared/lib/phone";

/** C-1. 아이디는 전화번호다. 하이픈을 넣어 입력해도 서버가 정규화한다. */
export default function LoginPage() {
  const { user, loading, signIn } = useAuth();
  const navigate = useNavigate();

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      const me = await signIn(loginId, password);
      navigate(me.mustChangePassword ? "/password" : homePathOf(me.role), { replace: true });
    } catch (e) {
      setError(errorMessage(e, "로그인에 실패했습니다."));
    } finally {
      setPending(false);
    }
  }

  if (loading) return <FullScreenLoader />;
  if (user) {
    return <Navigate to={user.mustChangePassword ? "/password" : homePathOf(user.role)} replace />;
  }

  return (
    // 처음 만나는 화면이라 앱바의 남색을 전면에 쓴다. 흰 카드 하나만 떠 있다
    <div className="flex min-h-screen flex-col items-center justify-center bg-banner p-4">
      <div className="w-full max-w-sm">
        <div className="mb-5 flex items-center gap-2.5 px-1 text-white">
          <span
            aria-hidden="true"
            className="grid h-9 w-9 place-items-center rounded-xl bg-white/15
                       ring-1 ring-inset ring-white/25"
          >
            <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor">
              <path
                d="M5 12.5l4.2 4.2L19 7"
                strokeWidth="2.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </span>
          <span className="text-lg font-extrabold tracking-[-0.02em]">{ACADEMY_NAME}</span>
        </div>

        <div className="card p-5">
          <h1 className="text-xl font-bold tracking-[-0.01em] text-brand-900">로그인</h1>
          <p className="mt-1 text-sm text-slate-500">전화번호로 로그인합니다.</p>

          <form onSubmit={handleSubmit} className="mt-5 space-y-4">
            <TextField
              label="전화번호"
              type="tel"
              inputMode="numeric"
              autoComplete="username"
              placeholder="010-1234-5678"
              value={loginId}
              onChange={(e) => setLoginId(formatPhone(e.target.value))}
              required
            />
            <TextField
              label="비밀번호"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <FormError message={error} />
            <SubmitButton pending={pending}>로그인</SubmitButton>
          </form>

          <div className="mt-5 space-y-1.5 border-t border-slate-100 pt-4 text-sm text-slate-500">
            <p>
              처음이신가요?{" "}
              <Link to="/signup" className="font-semibold text-brand-600 underline">
                회원가입
              </Link>
            </p>
            {/* 자동 재설정 경로는 없다. 이메일·SMS가 모두 범위 밖이다. */}
            <p>비밀번호를 잊으셨나요? 선생님께 문의해 주세요.</p>
          </div>
        </div>

        {/* 남색 위 흰 글씨. /50이면 대비가 겨우 걸친다 — 법정 고지라 넉넉히 둔다 */}
        <div className="mt-5 flex justify-center gap-3 text-xs text-white/70">
          <Link to="/terms" className="underline hover:text-white/80">
            이용약관
          </Link>
          <Link to="/privacy" className="underline hover:text-white/80">
            개인정보처리방침
          </Link>
        </div>
      </div>
    </div>
  );
}
