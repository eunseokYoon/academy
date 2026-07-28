import { Outlet } from "react-router-dom";

/**
 * 선생님 화면만 데스크톱 활용도가 높다 (숙제 확인 격자).
 * 모바일에서도 동작하되 넓은 화면에서 폭이 늘어난다.
 */
export default function TeacherLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-4 py-3">
        <h1 className="text-base font-semibold text-slate-900">선생님</h1>
      </header>
      <main className="mx-auto w-full max-w-screen-sm p-4 md:max-w-screen-xl">
        <Outlet />
      </main>
    </div>
  );
}
