import { Outlet } from "react-router-dom";
import { SelectedChildProvider } from "../../shared/auth/SelectedChildContext";
import { AppBar } from "../../shared/components/AppBar";
import { BottomTabBar } from "../../shared/components/BottomTabBar";
import type { BottomTab } from "../../shared/components/BottomTabBar";

/**
 * 하단 탭 바 다섯 개. <b>여섯 번째를 넣지 마라</b> — 스크롤되는 순간 하단 바의 값어치인
 * 위치 기억이 사라진다. 숙제와 공지는 홈의 QuickRail에 있다.
 *
 * <p>세 번째 자리가 숙제에서 주간 레포트로 바뀌었다. 레포트가 그 주 수업·테스트·숙제를
 * 한 장에 담아서, 학부모가 매주 여는 화면이 이쪽이다. 숙제만 따로 보는 건 레일에 남아
 * 있고 미완료 개수 점도 거기 붙는다.
 *
 * <p>주간 레포트는 학부모도 보지만 <b>영상은 빠진다</b> — 응답의 embedUrl이 null이다.
 * 수강 후기는 여전히 제외다.
 * KW-Study(공부 시간·랭킹) 항목도 없다 — 참고 디자인에 있더라도 넣지 마라.
 */
const TABS: BottomTab[] = [
  { to: "/parent", icon: "home", label: "홈", end: true },
  { to: "/parent/schedule", icon: "calendar", label: "일정" },
  { to: "/parent/lessons", icon: "book", label: "레포트" },
  { to: "/parent/scores", icon: "chart", label: "성적" },
  { to: "/parent/me", icon: "user", label: "내 정보" },
];

/**
 * 모바일 우선. 이동은 전부 아래에 있다 — 앱바는 제목 줄만 남았다.
 *
 * <p>main의 pb-24는 하단 바가 덮는 높이다. 없으면 마지막 카드가 탭 바에 가린다.
 *
 * <p><b>overflow-x-clip을 빼지 마라.</b> 홈의 `.field`가 100vw로 번져 main의
 * max-w-screen-sm을 뚫고 나간다 — 이게 없으면 가로 스크롤바가 생기고, 그 스크롤바
 * 폭만큼 100vw가 또 넘쳐서 계속 어긋난다. index.css의 `.field` 주석이 짝이다.
 */
export default function ParentLayout() {
  return (
    <SelectedChildProvider>
      <div className="min-h-screen overflow-x-clip bg-paper">
        <AppBar role="학부모" />
        <main className="mx-auto w-full max-w-screen-sm p-4 pb-24">
          <Outlet />
        </main>
        <BottomTabBar tabs={TABS} />
      </div>
    </SelectedChildProvider>
  );
}
