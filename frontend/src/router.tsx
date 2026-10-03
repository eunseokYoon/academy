import { createBrowserRouter } from "react-router-dom";
import { RequireAuth, RoleGuard } from "./shared/auth/RoleGuard";
import { RoleRedirect } from "./shared/auth/RoleRedirect";
import LoginPage from "./routes/auth/LoginPage";
import SignupPage from "./routes/auth/SignupPage";
import PasswordPage from "./routes/auth/PasswordPage";
import TermsPage from "./routes/auth/TermsPage";
import PrivacyPage from "./routes/auth/PrivacyPage";
import AccountDeletionPage from "./routes/auth/AccountDeletionPage";
import StudentLayout from "./routes/student/StudentLayout";
import StudentAttendancePage from "./routes/student/StudentAttendancePage";
import StudentClinicPage from "./routes/student/StudentClinicPage";
import StudentHomePage from "./routes/student/StudentHomePage";
import StudentNoticePage from "./routes/student/StudentNoticePage";
import StudentHomeworkPage from "./routes/student/StudentHomeworkPage";
import StudentHomeworkDetailPage from "./routes/student/StudentHomeworkDetailPage";
import StudentLessonPage from "./routes/student/StudentLessonPage";
import StudentLessonDetailPage from "./routes/student/StudentLessonDetailPage";
import StudentScorePage from "./routes/student/StudentScorePage";
import StudentOnlineTestPage from "./routes/student/StudentOnlineTestPage";
import StudentOnlineTestTakePage from "./routes/student/StudentOnlineTestTakePage";
import StudentQnaPage from "./routes/student/StudentQnaPage";
import StudentQnaDetailPage from "./routes/student/StudentQnaDetailPage";
import ParentLayout from "./routes/parent/ParentLayout";
import ParentHomePage from "./routes/parent/ParentHomePage";
import ParentMePage from "./routes/parent/ParentMePage";
import ParentNoticePage from "./routes/parent/ParentNoticePage";
import ParentReportPage from "./routes/parent/ParentReportPage";
import ParentSchedulePage from "./routes/parent/ParentSchedulePage";
import ParentHomeworkPage from "./routes/parent/ParentHomeworkPage";
import ParentScorePage from "./routes/parent/ParentScorePage";
import TeacherLayout from "./routes/teacher/TeacherLayout";
import DashboardPage from "./routes/teacher/DashboardPage";
import NoticePage from "./routes/teacher/notices/NoticePage";
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
import HomeworkGridPage from "./routes/teacher/homeworks/HomeworkGridPage";
import RegularExamPage from "./routes/teacher/regular-exams/RegularExamPage";
import ScorePage from "./routes/teacher/scores/ScorePage";
import ExamSchedulePage from "./routes/teacher/exams/ExamSchedulePage";
import OnlineTestListPage from "./routes/teacher/onlinetests/OnlineTestListPage";
import OnlineTestDetailPage from "./routes/teacher/onlinetests/OnlineTestDetailPage";
import QnaListPage from "./routes/teacher/qna/QnaListPage";
import QnaDetailPage from "./routes/teacher/qna/QnaDetailPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/terms", element: <TermsPage /> },
  { path: "/privacy", element: <PrivacyPage /> },
  // 구글플레이가 요구하는 앱 밖 계정 삭제 안내. 로그인 없이 열린다
  { path: "/account-deletion", element: <AccountDeletionPage /> },
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
      { index: true, element: <StudentHomePage /> },
      { path: "homeworks", element: <StudentHomeworkPage /> },
      { path: "homeworks/:homeworkId", element: <StudentHomeworkDetailPage /> },
      { path: "attendances", element: <StudentAttendancePage /> },
      { path: "clinics", element: <StudentClinicPage /> },
      { path: "lessons", element: <StudentLessonPage /> },
      { path: "lessons/:lessonId", element: <StudentLessonDetailPage /> },
      { path: "scores", element: <StudentScorePage /> },
      { path: "online-tests", element: <StudentOnlineTestPage /> },
      { path: "online-tests/:testId", element: <StudentOnlineTestTakePage /> },
      { path: "notices", element: <StudentNoticePage /> },
      { path: "qna", element: <StudentQnaPage /> },
      { path: "qna/:postId", element: <StudentQnaDetailPage /> },
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
      { index: true, element: <ParentHomePage /> },
      { path: "schedule", element: <ParentSchedulePage /> },
      { path: "homeworks", element: <ParentHomeworkPage /> },
      { path: "lessons", element: <ParentReportPage /> },
      { path: "scores", element: <ParentScorePage /> },
      { path: "notices", element: <ParentNoticePage /> },
      { path: "me", element: <ParentMePage /> },
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
      { index: true, element: <DashboardPage /> },
      { path: "students", element: <StudentListPage /> },
      { path: "students/new", element: <StudentNewPage /> },
      { path: "students/:studentId", element: <StudentDetailPage /> },
      { path: "class-rooms", element: <ClassRoomListPage /> },
      { path: "class-rooms/:classRoomId", element: <ClassRoomDetailPage /> },
      { path: "lessons", element: <LessonListPage /> },
      { path: "lessons/:lessonId", element: <LessonDetailPage /> },
      { path: "attendance", element: <AttendancePage /> },
      // 숙제 탭의 메인은 채점이다. 선생님이 매 수업 여는 화면이라 목록보다 앞에 온다.
      // "list"는 정적 세그먼트라 :homeworkId보다 우선 매칭된다 — 순서에 기대지 않는다
      { path: "homeworks", element: <HomeworkGridPage /> },
      { path: "homeworks/list", element: <HomeworkListPage /> },
      { path: "homeworks/:homeworkId", element: <HomeworkDetailPage /> },
      { path: "clinics", element: <ClinicPage /> },
      { path: "scores", element: <ScorePage /> },
      { path: "regular-exams", element: <RegularExamPage /> },
      { path: "exam-schedules", element: <ExamSchedulePage /> },
      { path: "online-tests", element: <OnlineTestListPage /> },
      { path: "online-tests/:testId", element: <OnlineTestDetailPage /> },
      { path: "notices", element: <NoticePage /> },
      { path: "qna", element: <QnaListPage /> },
      { path: "qna/:postId", element: <QnaDetailPage /> },
    ],
  },
  { path: "/", element: <RoleRedirect /> },
  { path: "*", element: <RoleRedirect /> },
]);
