import { Outlet } from "react-router-dom";
import { useAuth } from "../../shared/auth/AuthContext";
import { AppBar, AppBarAction } from "../../shared/components/AppBar";
import type { AppBarTab } from "../../shared/components/AppBar";

const TABS: AppBarTab[] = [
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
    <div className="min-h-screen bg-paper">
      <AppBar
        role="선생님"
        tabs={TABS}
        wide
        action={<AppBarAction onClick={() => void signOut()}>로그아웃</AppBarAction>}
      />
      <main className="mx-auto w-full max-w-screen-sm p-4 md:max-w-screen-xl">
        <Outlet />
      </main>
    </div>
  );
}
