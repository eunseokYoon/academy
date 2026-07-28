import { createBrowserRouter } from "react-router-dom";
import { RoleGuard } from "./shared/auth/RoleGuard";
import { RoleRedirect } from "./shared/auth/RoleRedirect";
import LoginPage from "./routes/auth/LoginPage";
import TermsPage from "./routes/auth/TermsPage";
import PrivacyPage from "./routes/auth/PrivacyPage";
import StudentLayout from "./routes/student/StudentLayout";
import ParentLayout from "./routes/parent/ParentLayout";
import TeacherLayout from "./routes/teacher/TeacherLayout";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/terms", element: <TermsPage /> },
  { path: "/privacy", element: <PrivacyPage /> },
  {
    path: "/student",
    element: (
      <RoleGuard role="STUDENT">
        <StudentLayout />
      </RoleGuard>
    ),
    children: [
      /* Phase 5, 6, 7에서 추가 */
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
      /* Phase 4, 5, 6, 7에서 추가 */
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
      /* Phase 3~7에서 추가 */
    ],
  },
  { path: "/", element: <RoleRedirect /> },
]);
