/** 로그인 판정이 끝나기 전에 보여준다. 이게 없으면 로그인 화면이 한 번 깜빡인다. */
export function FullScreenLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper">
      <p className="text-sm text-slate-400">불러오는 중…</p>
    </div>
  );
}
