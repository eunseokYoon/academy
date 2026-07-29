import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { errorMessage } from "../../shared/api/errors";
import { homePathOf, useAuth } from "../../shared/auth/AuthContext";
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
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold text-slate-900">로그인</h1>
        <p className="mt-1 text-sm text-slate-500">전화번호로 로그인합니다.</p>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
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

        <div className="mt-6 space-y-2 text-sm text-slate-500">
          <p>
            처음이신가요?{" "}
            <Link to="/signup" className="font-medium text-slate-900 underline">
              회원가입
            </Link>
          </p>
          {/* 자동 재설정 경로는 없다. 이메일·SMS가 모두 범위 밖이다. */}
          <p>비밀번호를 잊으셨나요? 선생님께 문의해 주세요.</p>
        </div>

        <div className="mt-8 flex gap-3 text-xs text-slate-400">
          <Link to="/terms" className="underline">
            이용약관
          </Link>
          <Link to="/privacy" className="underline">
            개인정보처리방침
          </Link>
        </div>
      </div>
    </div>
  );
}
