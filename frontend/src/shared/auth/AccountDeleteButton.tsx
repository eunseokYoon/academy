import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { post } from "../api/client";
import { errorMessage } from "../api/errors";
import { FormError } from "../components/FormError";
import { Modal } from "../components/Modal";
import { SubmitButton } from "../components/SubmitButton";
import { TextField } from "../components/TextField";
import { clearAccessToken } from "./token";

/**
 * 학생·학부모 본인의 계정 삭제(2026-10-03). 앱스토어·구글플레이가 요구한다.
 *
 * <p><b>로그인 계정만 지운다.</b> 학습 기록(출석·숙제·성적)은 처리방침의 보관 기간 동안 남고
 * 선생님 화면에 미가입 학생으로 보인다 — 서버 `AccountDeletionService`. 그 사실을 모달에 적는다.
 *
 * <p>성공하면 화면을 통째로 새로 연다. 쿼리 캐시에 남은 앞 사람 데이터를 버리는 가장 확실한 길이다.
 */
export function AccountDeleteButton() {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");

  const remove = useMutation({
    mutationFn: () => post<void>("/auth/account/delete", { password }),
    onSuccess: () => {
      clearAccessToken();
      window.location.assign("/login");
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (password !== "") remove.mutate();
  }

  return (
    <>
      <button
        type="button"
        onClick={() => {
          setPassword("");
          remove.reset();
          setOpen(true);
        }}
        className="text-slate-400 underline"
      >
        계정 삭제
      </button>
      {open && (
        <Modal title="계정 삭제" onClose={() => setOpen(false)}>
          <form onSubmit={submit} className="space-y-3">
            <div className="space-y-1.5 text-sm text-slate-700">
              <p>로그인 계정(아이디·비밀번호)이 바로 지워지고 되돌릴 수 없습니다.</p>
              <p className="text-slate-500">
                수업에서 쌓인 출석·숙제·성적 기록은 학원 운영을 위해 개인정보처리방침에 적힌 기간
                동안 보관된 뒤 삭제됩니다.
              </p>
            </div>
            <TextField
              label="비밀번호 확인"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoFocus
            />
            {remove.isError && <FormError message={errorMessage(remove.error)} />}
            <SubmitButton pending={remove.isPending} disabled={password === ""}>
              계정 삭제
            </SubmitButton>
          </form>
        </Modal>
      )}
    </>
  );
}
