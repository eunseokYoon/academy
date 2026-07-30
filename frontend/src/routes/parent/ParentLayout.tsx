import { NavLink, Outlet } from "react-router-dom";
import { SelectedChildProvider } from "../../shared/auth/SelectedChildContext";

/**
 * 학부모 탭은 홈 + 메뉴 4개다. 수업영상·레포트, 수업 자료실, 수강 후기는 제외됐다.
 * KW-Study(공부 시간·랭킹) 항목도 없다 — 참고 디자인에 있더라도 넣지 마라.
 */
const TABS = [
  { to: "/parent", label: "홈", end: true },
  { to: "/parent/schedule", label: "일정 · 출석" },
  { to: "/parent/homeworks", label: "숙제" },
  { to: "/parent/scores", label: "테스트 결과" },
  { to: "/parent/notices", label: "공지" },
  { to: "/parent/me", label: "내 정보" },
];

/** 모바일 우선. 탭이 늘어 가로 스크롤을 허용한다. */
export default function ParentLayout() {
  return (
    <SelectedChildProvider>
      <div className="min-h-screen bg-slate-50">
        <header className="border-b border-slate-200 bg-white">
          <div className="mx-auto w-full max-w-screen-sm px-4 py-3">
            <h1 className="text-base font-semibold text-slate-900">학부모</h1>
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
    </SelectedChildProvider>
  );
}
