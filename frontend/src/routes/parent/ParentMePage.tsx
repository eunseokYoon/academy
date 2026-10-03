import { useState } from "react";
import { AccountDeleteButton } from "../../shared/auth/AccountDeleteButton";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { get, patch } from "../../shared/api/client";
import { errorMessage } from "../../shared/api/errors";
import { useAuth } from "../../shared/auth/AuthContext";
import { FormError } from "../../shared/components/FormError";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { SubmitButton } from "../../shared/components/SubmitButton";
import { TextField } from "../../shared/components/TextField";
import { digitsOnly, formatPhone } from "../../shared/lib/phone";

interface ParentMe {
  id: number;
  name: string;
  phone: string;
  children: { studentId: number; name: string }[];
}

/** P-5. 자녀 목록과 연락처 변경. */
export default function ParentMePage() {
  const queryClient = useQueryClient();
  const { reload, signOut } = useAuth();

  const { data, isPending } = useQuery({
    queryKey: ["parent", "me"],
    queryFn: () => get<ParentMe>("/parent/me"),
  });

  const [phone, setPhone] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (value: string) => patch<ParentMe>("/parent/me", { phone: digitsOnly(value) }),
    onSuccess: async (updated) => {
      setError(null);
      setDone(true);
      setPhone(formatPhone(updated.phone));
      queryClient.setQueryData(["parent", "me"], updated);
      await queryClient.invalidateQueries({ queryKey: ["parent", "children"] });
      await reload();
    },
    onError: (e) => {
      setDone(false);
      setError(errorMessage(e, "연락처를 변경하지 못했습니다."));
    },
  });

  if (isPending || !data) return <p className="text-sm text-slate-400">불러오는 중…</p>;

  const value = phone ?? formatPhone(data.phone);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(value);
  }

  return (
    <div className="space-y-5">
      <PageTitle>{data.name} 님</PageTitle>

      <section>
        <SectionHead tone="brand" title="자녀" count={data.children.length} />
        <TintBlock tone="brand">
          {data.children.length === 0 ? (
            <p className="px-3.5 py-4 text-sm text-brand-700/70">
              연결된 자녀가 없습니다. 선생님께 문의해 주세요.
            </p>
          ) : (
            data.children.map((child) => (
              <p
                key={child.studentId}
                className="px-3.5 py-3 text-[14px] font-semibold text-brand-950"
              >
                {child.name}
              </p>
            ))
          )}
        </TintBlock>
      </section>

      <section>
        <SectionHead tone="neutral" title="연락처 변경" />
        <form onSubmit={handleSubmit} className="space-y-3 rounded-2xl bg-white p-4 shadow-card">
          <TextField
            label="전화번호"
            type="tel"
            inputMode="numeric"
            hint="이 번호가 로그인 아이디입니다. 바꾸면 다음 로그인부터 새 번호를 쓰세요."
            value={value}
            onChange={(e) => {
              setPhone(formatPhone(e.target.value));
              setDone(false);
            }}
            required
          />
          <FormError message={error} />
          {done && (
            <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              변경되었습니다. 이제 새 번호로 로그인하세요.
            </p>
          )}
          <SubmitButton pending={mutation.isPending}>변경하기</SubmitButton>
        </form>
      </section>


      <div className="flex items-center justify-between text-sm">
        <Link to="/privacy" className="text-slate-500 underline">
          개인정보처리방침
        </Link>
        <div className="flex items-center gap-4">
          {/* 스토어 심사가 요구하는 앱 안 계정 삭제(2026-10-03) */}
          <AccountDeleteButton />
          <button type="button" onClick={() => void signOut()} className="text-slate-500 underline">
            로그아웃
          </button>
        </div>
      </div>
    </div>
  );
}
