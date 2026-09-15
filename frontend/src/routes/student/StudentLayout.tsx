import { Outlet } from "react-router-dom";
import { AppBar } from "../../shared/components/AppBar";
import { BottomTabBar } from "../../shared/components/BottomTabBar";
import type { BottomTab } from "../../shared/components/BottomTabBar";

/**
 * 하단 탭 바 다섯 개. <b>여섯 번째를 넣지 마라</b> — 넣는 순간 스크롤이 필요해지고,
 * 하단 바의 값어치인 위치 기억이 사라진다. 나머지 화면은 홈의 QuickRail에 다 있다.
 *
 * <p>2026-09-10에 순서를 바꿨다. 테스트·출석이 레일로 내려가고 수업·질문이 올라왔다.
 *
 * <p>/student/scores는 실제로는 "내 정보 · 성적"이고 <b>학생의 유일한 로그아웃 경로</b>다.
 * 탭 라벨만 「성적」으로 바꿨고 <b>화면 제목은 「내 정보 · 성적」을 유지한다</b> —
 * 들어가면 내 정보 카드와 로그아웃이 그대로 보여야 공용 PC나 형제 폰에서
 * 계정을 내려놓을 수 있다. 화면 제목까지 「성적」으로 바꾸지 마라.
 */
const TABS: BottomTab[] = [
  { to: "/student", icon: "home", label: "홈", end: true },
  { to: "/student/homeworks", icon: "homework", label: "숙제" },
  { to: "/student/lessons", icon: "video", label: "수업" },
  { to: "/student/scores", icon: "chart", label: "성적" },
  { to: "/student/qna", icon: "question", label: "질문" },
];

/**
 * 모바일 우선. 이동은 전부 아래에 있다 — 앱바는 제목 줄만 남았다.
 *
 * <p>main의 pb-24는 하단 바가 덮는 높이다. 없으면 스크롤을 끝까지 내려도
 * 마지막 카드가 탭 바에 가려 안 보인다.
 *
 * <p><b>overflow-x-clip을 빼지 마라.</b> 홈의 `.field`가 100vw로 번져 main의
 * max-w-screen-sm을 뚫고 나간다 — 이게 없으면 가로 스크롤바가 생기고, 그 스크롤바
 * 폭만큼 100vw가 또 넘쳐서 계속 어긋난다. index.css의 `.field` 주석이 짝이다.
 */
export default function StudentLayout() {
  return (
    <div className="min-h-screen overflow-x-clip bg-paper">
      <AppBar role="학생" />
      <main className="mx-auto w-full max-w-screen-sm p-4 pb-24">
        <Outlet />
      </main>
      <BottomTabBar tabs={TABS} />
    </div>
  );
}
