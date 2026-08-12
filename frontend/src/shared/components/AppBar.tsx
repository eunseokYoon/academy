import { NavLink } from "react-router-dom";
import type { ReactNode } from "react";
import { LogoBadge, Wordmark } from "./Logo";

export interface AppBarTab {
  to: string;
  label: string;
  end?: boolean;
}

interface Props {
  /** 워드마크 옆 역할 칩. "학생" · "학부모" · "선생님" */
  role: string;
  /**
   * 상단 탭 줄. <b>선생님 화면만 쓴다.</b> 학생·학부모는 하단 탭 바로 옮겼다 —
   * 폰에서 화면 맨 위는 엄지가 안 닿고, 탭이 9개라 가로로 흘러 뒤쪽은 아무도 못 찾았다.
   * 선생님은 탭이 13개에 넓은 표를 쓰는 데스크톱 화면이라 그대로 둔다.
   */
  tabs?: AppBarTab[];
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
 *
 * <p>tabs를 안 넘기면 제목 줄만 남는다(학생·학부모). 이동은 하단 탭 바가 맡는다.
 */
export function AppBar({ role, tabs, action, wide }: Props) {
  const width = wide ? "max-w-screen-sm md:max-w-screen-xl" : "max-w-screen-sm";

  return (
    <header className="bg-brand-900 pb-8 text-white">
      <div className={`mx-auto flex w-full ${width} items-center justify-between gap-3 px-4 py-3`}>
        <div className="flex min-w-0 items-center gap-2.5">
          <Mark />
          <div className="flex min-w-0 items-baseline gap-2">
            <Wordmark />
            <span className="shrink-0 text-xs font-medium text-brand-300">{role}</span>
          </div>
        </div>
        {action}
      </div>

      {/*
        탭이 12개라 화면 밖으로 넘친다. 잘린 자리에 그라데이션을 덮어
        "옆에 더 있다"를 보이게 한다 — 없으면 뒤쪽 탭의 존재를 아무도 모른다.
      */}
      {tabs && tabs.length > 0 && (
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
      )}
    </header>
  );
}

/**
 * 로고의 NJ 모노그램.
 *
 * <p>흰 테두리를 두른 배지다. 앱바가 로고 판과 같은 남색(brand-900)이라 마크를 그대로
 * 얹으면 획만 있는 도형이 배경에 떠 버린다 — 테두리가 가장자리를 만들어 준다.
 * <b>안을 흰색으로 채우지 마라</b>(스티커가 된다). 이유는 LogoBadge 주석에 있다.
 */
function Mark() {
  return <LogoBadge />;
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
