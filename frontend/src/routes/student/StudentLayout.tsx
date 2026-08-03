import { Outlet } from "react-router-dom";
import { AppBar } from "../../shared/components/AppBar";
import type { AppBarTab } from "../../shared/components/AppBar";

const TABS: AppBarTab[] = [
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
    <div className="min-h-screen bg-paper">
      <AppBar role="학생" tabs={TABS} />
      <main className="mx-auto w-full max-w-screen-sm p-4">
        <Outlet />
      </main>
    </div>
  );
}
