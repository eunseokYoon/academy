import { NavLink, Outlet } from "react-router-dom";

const TABS = [
  { to: "/student", label: "홈", end: true },
  { to: "/student/homeworks", label: "숙제" },
  { to: "/student/lessons", label: "수업영상" },
  { to: "/student/online-tests", label: "테스트" },
  { to: "/student/attendances", label: "출석" },
  { to: "/student/clinics", label: "클리닉" },
  { to: "/student/materials", label: "자료실" },
  { to: "/student/notices", label: "공지" },
  { to: "/student/scores", label: "내 정보" },
];

/** 모바일 우선. 탭이 많아 가로 스크롤을 허용한다. */
export default function StudentLayout() {
  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto w-full max-w-screen-sm px-4 py-3">
          <h1 className="text-base font-semibold text-slate-900">학생</h1>
        </div>
        <nav className="mx-auto flex w-full max-w-screen-sm gap-1 overflow-x-auto px-2">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
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
      <main className="mx-auto w-full max-w-screen-sm p-4">
        <Outlet />
      </main>
    </div>
  );
}
