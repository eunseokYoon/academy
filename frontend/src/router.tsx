import { createBrowserRouter, Navigate } from "react-router-dom";
import { RequireAuth, RoleGuard } from "./shared/auth/RoleGuard";
import { RoleRedirect } from "./shared/auth/RoleRedirect";
import LoginPage from "./routes/auth/LoginPage";
import SignupPage from "./routes/auth/SignupPage";
import PasswordPage from "./routes/auth/PasswordPage";
import TermsPage from "./routes/auth/TermsPage";
import PrivacyPage from "./routes/auth/PrivacyPage";
import StudentLayout from "./routes/student/StudentLayout";
import StudentAttendancePage from "./routes/student/StudentAttendancePage";
import StudentClinicPage from "./routes/student/StudentClinicPage";
import StudentHomeworkPage from "./routes/student/StudentHomeworkPage";
import StudentHomeworkDetailPage from "./routes/student/StudentHomeworkDetailPage";
import ParentLayout from "./routes/parent/ParentLayout";
import ParentMePage from "./routes/parent/ParentMePage";
import ParentSchedulePage from "./routes/parent/ParentSchedulePage";
import ParentHomeworkPage from "./routes/parent/ParentHomeworkPage";
import TeacherLayout from "./routes/teacher/TeacherLayout";
import AttendancePage from "./routes/teacher/attendance/AttendancePage";
import ClinicPage from "./routes/teacher/clinics/ClinicPage";
import StudentListPage from "./routes/teacher/students/StudentListPage";
import StudentNewPage from "./routes/teacher/students/StudentNewPage";
import StudentDetailPage from "./routes/teacher/students/StudentDetailPage";
import ClassRoomListPage from "./routes/teacher/classrooms/ClassRoomListPage";
import ClassRoomDetailPage from "./routes/teacher/classrooms/ClassRoomDetailPage";
import LessonListPage from "./routes/teacher/lessons/LessonListPage";
import LessonDetailPage from "./routes/teacher/lessons/LessonDetailPage";
import HomeworkListPage from "./routes/teacher/homeworks/HomeworkListPage";
import HomeworkDetailPage from "./routes/teacher/homeworks/HomeworkDetailPage";

/** 다음 Phase에서 실제 홈으로 교체한다. */
function ComingSoon({ phase }: { phase: string }) {
  return <p className="text-sm text-slate-500">{phase}에서 채웁니다.</p>;
}

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/terms", element: <TermsPage /> },
  { path: "/privacy", element: <PrivacyPage /> },
  {
    // 역할을 가리지 않는다. 초기 비밀번호 상태에서 유일하게 열리는 화면이다
    path: "/password",
    element: (
      <RequireAuth>
        <PasswordPage />
      </RequireAuth>
    ),
  },
  {
    path: "/student",
    element: (
      <RoleGuard role="STUDENT">
        <StudentLayout />
      </RoleGuard>
    ),
    children: [
      { index: true, element: <ComingSoon phase="Phase 7" /> },
      { path: "homeworks", element: <StudentHomeworkPage /> },
      { path: "homeworks/:homeworkId", element: <StudentHomeworkDetailPage /> },
      { path: "attendances", element: <StudentAttendancePage /> },
      { path: "clinics", element: <StudentClinicPage /> },
      /* Phase 6, 7에서 추가 */
    ],
  },
  {
    path: "/parent",
    element: (
      <RoleGuard role="PARENT">
        <ParentLayout />
      </RoleGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/parent/schedule" replace /> },
      { path: "schedule", element: <ParentSchedulePage /> },
      { path: "homeworks", element: <ParentHomeworkPage /> },
      { path: "me", element: <ParentMePage /> },
      /* Phase 6, 7에서 추가 */
    ],
  },
  {
    path: "/teacher",
    element: (
      <RoleGuard role="TEACHER">
        <TeacherLayout />
      </RoleGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/teacher/students" replace /> },
      { path: "students", element: <StudentListPage /> },
      { path: "students/new", element: <StudentNewPage /> },
      { path: "students/:studentId", element: <StudentDetailPage /> },
      { path: "class-rooms", element: <ClassRoomListPage /> },
      { path: "class-rooms/:classRoomId", element: <ClassRoomDetailPage /> },
      { path: "lessons", element: <LessonListPage /> },
      { path: "lessons/:lessonId", element: <LessonDetailPage /> },
      { path: "attendance", element: <AttendancePage /> },
      { path: "homeworks", element: <HomeworkListPage /> },
      { path: "homeworks/:homeworkId", element: <HomeworkDetailPage /> },
      { path: "clinics", element: <ClinicPage /> },
      /* Phase 6~7에서 추가 */
    ],
  },
  { path: "/", element: <RoleRedirect /> },
  { path: "*", element: <RoleRedirect /> },
]);
