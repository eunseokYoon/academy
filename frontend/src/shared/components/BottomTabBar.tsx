import { NavLink } from "react-router-dom";
import { Icon } from "./Icon";
import type { IconName } from "./Icon";

export interface BottomTab {
  to: string;
  icon: IconName;
  label: string;
  end?: boolean;
}

/**
 * 엄지 자리에 고정된 하단 탭 바. 학생·학부모 화면의 <b>주 내비게이션</b>이다.
 * 상단 앱바의 탭 줄을 대체한 것이지 더한 게 아니다.
 *
 * <p><b>스크롤되면 안 된다. 그래서 다섯 개까지다.</b> 하단 바의 값어치는 "숙제는 항상
 * 왼쪽에서 두 번째"라는 위치 기억인데, 옆으로 넘어가는 순간 그게 사라지고 상단 탭이
 * 갖고 있던 문제(다 안 보인다)를 그대로 물려받는다. 넘치는 곳은 홈의 QuickRail이 받는다.
 * 여섯 번째를 넣고 싶으면 다섯 중 하나를 레일로 내려라.
 *
 * <p>현재 위치는 로고의 주황이다. 이 색이 뜻하는 건 "지금 여기" 하나뿐이라
 * amber(경고)·red(위험)와 부딪히지 않는다. 버튼·링크로 넓히지 마라.
 *
 * <p>아이폰 홈 인디케이터를 피하려고 safe-area만큼 아래를 띄운다.
 * index.html의 viewport-fit=cover가 있어야 이 값이 0이 아니다.
 */
export function BottomTabBar({ tabs }: { tabs: BottomTab[] }) {
  return (
    <nav
      aria-label="주 메뉴"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200/80 bg-white/95
                 pb-[env(safe-area-inset-bottom)] backdrop-blur"
    >
      <ul className="mx-auto flex w-full max-w-screen-sm">
        {tabs.map((tab) => (
          <li key={tab.to} className="flex-1">
            <NavLink
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                `flex flex-col items-center gap-1 py-2 transition-colors ${
                  isActive ? "text-accent-500" : "text-slate-400"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  {/*
                    선택된 탭만 색이 아니라 굵기까지 바뀐다. 색만으로 구분하면
                    색각 이상이 있거나 화면이 밝은 야외에서 어디 있는지 알 수 없다.
                  */}
                  <Icon
                    name={tab.icon}
                    className={isActive ? "h-[22px] w-[22px] stroke-[2.2]" : "h-[22px] w-[22px]"}
                  />
                  <span
                    className={`text-[10px] leading-none ${
                      isActive ? "font-bold" : "font-medium"
                    }`}
                  >
                    {tab.label}
                  </span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
