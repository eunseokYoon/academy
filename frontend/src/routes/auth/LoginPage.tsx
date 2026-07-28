import { useQuery } from "@tanstack/react-query";
import { get } from "../../shared/api/client";

/**
 * Phase 0 자리표시자. 실제 로그인 폼은 Phase 2(C-1)에서 만든다.
 * 지금은 백엔드 연결과 CORS가 살아 있는지만 보여준다.
 */
export default function LoginPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["health"],
    queryFn: () => get<string>("/health"),
  });

  const status = isPending ? "확인 중…" : isError ? "연결 실패" : data;

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">로그인</h1>
        <p className="mt-2 text-sm text-slate-500">Phase 2에서 구현합니다.</p>
        <p className="mt-6 text-xs text-slate-400">
          백엔드 상태: <span className="font-mono">{status}</span>
        </p>
      </div>
    </div>
  );
}
