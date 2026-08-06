import { Outlet } from "react-router-dom";
import { SelectedChildProvider } from "../../shared/auth/SelectedChildContext";
import { AppBar } from "../../shared/components/AppBar";
import type { AppBarTab } from "../../shared/components/AppBar";

/**
 * 수업 레포트는 학부모도 본다. <b>단 영상은 빠진다</b> — 응답의 embedUrl이 null이다.
 * 수업 자료실, 수강 후기는 여전히 제외다.
 * KW-Study(공부 시간·랭킹) 항목도 없다 — 참고 디자인에 있더라도 넣지 마라.
 */
const TABS: AppBarTab[] = [
  { to: "/parent", label: "홈", end: true },
  { to: "/parent/schedule", label: "일정 · 출석" },
  { to: "/parent/homeworks", label: "숙제" },
  { to: "/parent/lessons", label: "수업 레포트" },
  { to: "/parent/scores", label: "테스트 결과" },
  { to: "/parent/notices", label: "공지" },
  { to: "/parent/me", label: "내 정보" },
];

/** 모바일 우선. 탭이 늘어 가로 스크롤을 허용한다. */
export default function ParentLayout() {
  return (
    <SelectedChildProvider>
      <div className="min-h-screen bg-paper">
        <AppBar role="학부모" tabs={TABS} />
        <main className="mx-auto w-full max-w-screen-sm p-4">
          <Outlet />
        </main>
      </div>
    </SelectedChildProvider>
  );
}
