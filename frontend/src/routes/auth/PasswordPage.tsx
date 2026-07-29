import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { patch } from "../../shared/api/client";
import { errorMessage } from "../../shared/api/errors";
import { useAuth } from "../../shared/auth/AuthContext";
import { FormError } from "../../shared/components/FormError";
import { SubmitButton } from "../../shared/components/SubmitButton";
import { TextField } from "../../shared/components/TextField";

const MIN_LENGTH = 8;

/**
 * C-2. 로그인 상태에서의 비밀번호 변경만 담당한다. 재설정 폼이 아니다.
 *
 * <p>변경에 성공하면 서버가 이 사용자의 리프레시 토큰을 전부 폐기한다(다른 기기 세션 차단).
 * 그래서 화면도 로그아웃 상태로 되돌리고 다시 로그인시킨다.
 */
export default function PasswordPage() {
  const { user, resetSession } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);

    if (newPassword.length < MIN_LENGTH) {
      setError(`새 비밀번호는 ${MIN_LENGTH}자 이상이어야 합니다.`);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("새 비밀번호가 서로 다릅니다.");
      return;
    }

    setPending(true);
    try {
      await patch<void>("/auth/password", { currentPassword, newPassword });
      // 서버가 이미 모든 토큰을 폐기했다. 로그아웃 API를 또 부르면 401만 받는다
      resetSession();
      navigate("/login", { replace: true });
    } catch (e) {
      setError(errorMessage(e, "비밀번호를 변경하지 못했습니다."));
      setPending(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-sm">
        <h1 className="text-2xl font-semibold text-slate-900">비밀번호 변경</h1>
        {user?.mustChangePassword && (
          <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
            초기 비밀번호를 쓰고 계십니다. 변경하셔야 나머지 화면을 볼 수 있습니다.
          </p>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <TextField
            label="현재 비밀번호"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />
          <TextField
            label="새 비밀번호"
            type="password"
            autoComplete="new-password"
            hint={`${MIN_LENGTH}자 이상`}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
          <TextField
            label="새 비밀번호 확인"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />
          <FormError message={error} />
          <SubmitButton pending={pending}>변경하기</SubmitButton>
        </form>

        <p className="mt-4 text-xs text-slate-500">
          변경하면 다른 기기의 로그인이 모두 해제됩니다. 새 비밀번호로 다시 로그인해 주세요.
        </p>
      </div>
    </div>
  );
}
