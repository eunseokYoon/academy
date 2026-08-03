import { NavLink } from "react-router-dom";
import type { ReactNode } from "react";
import { ACADEMY_NAME } from "../branding";

export interface AppBarTab {
  to: string;
  label: string;
  end?: boolean;
}

interface Props {
  /** 워드마크 옆 역할 칩. "학생" · "학부모" · "선생님" */
  role: string;
  tabs: AppBarTab[];
  /** 오른쪽 동작 버튼. 선생님 화면의 로그아웃 등. */
  action?: ReactNode;
  /** 선생님 화면만 넓은 폭을 쓴다 (숙제 확인 격자). */
  wide?: boolean;
}

/**
 * 학생·학부모·선생님이 공유하는 남색 앱바.
 *
 * <p>아래 <b>pb-8(32px)은 장식이 아니라 자리다.</b> 홈 화면의 히어로 카드가
 * `.hero-lift`(-mt-10)로 이 안까지 파고들어 띠에 걸쳐 앉는다. 이 디자인의 시그니처다.
 * 여기 패딩을 줄이면 카드가 탭을 덮는다 — index.css의 `.hero-lift` 주석을 같이 봐라.
 */
export function AppBar({ role, tabs, action, wide }: Props) {
  const width = wide ? "max-w-screen-sm md:max-w-screen-xl" : "max-w-screen-sm";

  return (
    <header className="bg-brand-900 pb-8 text-white">
      <div className={`mx-auto flex w-full ${width} items-center justify-between gap-3 px-4 py-3`}>
        <div className="flex min-w-0 items-center gap-2.5">
          <Mark />
          <div className="flex min-w-0 items-baseline gap-2">
            {/* 자간을 좁힌 800 무게. 워드마크는 본문과 다르게 읽혀야 한다 */}
            <span className="truncate text-[15px] font-extrabold tracking-[-0.02em]">
              {ACADEMY_NAME}
            </span>
            <span className="shrink-0 text-xs font-medium text-brand-300">{role}</span>
          </div>
        </div>
        {action}
      </div>

      {/*
        탭이 9~12개라 화면 밖으로 넘친다. 잘린 자리에 그라데이션을 덮어
        "옆에 더 있다"를 보이게 한다 — 없으면 뒤쪽 탭의 존재를 아무도 모른다.
      */}
      <div className="relative">
        <nav
          className={`no-scrollbar mx-auto flex w-full ${width} gap-1 overflow-x-auto px-2`}
          aria-label={`${role} 메뉴`}
        >
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                `shrink-0 rounded-t-md border-b-2 px-3 py-2 text-sm transition-colors ${
                  isActive
                    ? "border-white font-semibold text-white"
                    : "border-transparent text-white/55 hover:text-white/80"
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 right-0 w-10
                     bg-gradient-to-l from-brand-900 to-transparent"
        />
      </div>
    </header>
  );
}

/**
 * 상호가 미정이라 글자 모노그램을 못 쓴다. 이름과 무관한 도형 마크를 둔다.
 * 체크는 이 서비스가 실제로 하는 일(숙제·출석 확인)에서 왔다.
 */
function Mark() {
  return (
    <span
      aria-hidden="true"
      className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-white/15
                 ring-1 ring-inset ring-white/25"
    >
      <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor">
        <path
          d="M5 12.5l4.2 4.2L19 7"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  );
}

/** 앱바 오른쪽 버튼. 남색 위에서도 눌러지는 곳으로 보이게 테두리를 준다. */
export function AppBarAction({
  onClick,
  children,
}: {
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="shrink-0 rounded-lg border border-white/25 px-2.5 py-1.5 text-xs
                 font-medium text-white/90 transition-colors hover:bg-white/10"
    >
      {children}
    </button>
  );
}
