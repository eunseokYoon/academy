import { Outlet } from "react-router-dom";

/** 모바일 우선. 하위 화면은 Phase 4·5·6·7에서 추가한다. */
export default function ParentLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-3">
        <h1 className="text-base font-semibold text-slate-900">학부모</h1>
      </header>
      <main className="mx-auto w-full max-w-screen-sm p-4">
        <Outlet />
      </main>
    </div>
  );
}
