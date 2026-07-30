import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../shared/auth/AuthContext";

const TABS = [
  { to: "/teacher", label: "대시보드", end: true },
  { to: "/teacher/students", label: "학생" },
  { to: "/teacher/class-rooms", label: "반" },
  { to: "/teacher/lessons", label: "수업" },
  { to: "/teacher/attendance", label: "출석" },
  { to: "/teacher/homeworks", label: "숙제" },
  { to: "/teacher/clinics", label: "클리닉" },
  { to: "/teacher/scores", label: "성적" },
  { to: "/teacher/exam-schedules", label: "시험일정" },
  { to: "/teacher/online-tests", label: "온라인테스트" },
  { to: "/teacher/materials", label: "자료실" },
  { to: "/teacher/notices", label: "공지" },
];

/**
 * 선생님 화면만 데스크톱 활용도가 높다 (숙제 확인 격자).
 * 모바일에서도 동작하되 넓은 화면에서 폭이 늘어난다.
 */
export default function TeacherLayout() {
  const { signOut } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div
          className="mx-auto flex w-full max-w-screen-sm items-center justify-between px-4 py-3
                     md:max-w-screen-xl"
        >
          <h1 className="text-base font-semibold text-slate-900">선생님</h1>
          <button
            type="button"
            onClick={() => void signOut()}
            className="text-sm text-slate-500 underline"
          >
            로그아웃
          </button>
        </div>
        <nav className="mx-auto flex w-full max-w-screen-sm gap-1 overflow-x-auto px-2 md:max-w-screen-xl">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `shrink-0 border-b-2 px-3 py-2 text-sm ${
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
      <main className="mx-auto w-full max-w-screen-sm p-4 md:max-w-screen-xl">
        <Outlet />
      </main>
    </div>
  );
}
