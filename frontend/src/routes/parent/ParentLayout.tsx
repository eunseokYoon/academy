import { NavLink, Outlet } from "react-router-dom";
import { SelectedChildProvider } from "../../shared/auth/SelectedChildContext";

const TABS = [
  { to: "/parent/schedule", label: "일정 · 출석" },
  { to: "/parent/homeworks", label: "숙제" },
  { to: "/parent/me", label: "내 정보" },
];

/** 모바일 우선. 하위 화면은 Phase 5·6·7에서 더 붙는다. */
export default function ParentLayout() {
  return (
    <SelectedChildProvider>
      <div className="min-h-screen bg-slate-50">
        <header className="border-b border-slate-200 bg-white">
          <div className="mx-auto w-full max-w-screen-sm px-4 py-3">
            <h1 className="text-base font-semibold text-slate-900">학부모</h1>
          </div>
          <nav className="mx-auto flex w-full max-w-screen-sm gap-1 px-2">
            {TABS.map((tab) => (
              <NavLink
                key={tab.to}
                to={tab.to}
                className={({ isActive }) =>
                  `border-b-2 px-3 py-2 text-sm ${
                    isActive
                      ? "border-slate-900 font-medium text-slate-900"
                      : "border-transparent text-slate-500"
                  }`
                }
              >
                {tab.label}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="mx-auto w-full max-w-screen-sm p-4">
          <Outlet />
        </main>
      </div>
    </SelectedChildProvider>
  );
}
