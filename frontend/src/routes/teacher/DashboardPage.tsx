import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { DAY_LABELS } from "../../shared/date";
import { getDashboard } from "./api";
import type { TeacherDashboard } from "./api";

/**
 * 할 일 네 개. 0이면 흐리게 처리한다.
 *
 * <p>"확인 대기 숙제"는 없앴다(2026-08-09). 재제출은 학생이 내는 순간 ⭕가 되어
 * 선생님이 눌러야 할 것이 없다 — 남겨 두면 영영 줄지 않는 숫자가 된다.
 * 낸 사진·영상은 그리드 열 머리의 "N명 제출 · 보기"로 들어간다.
 */
const TODO_ITEMS: {
  key: keyof TeacherDashboard["todo"];
  label: string;
  to: string;
  unit: string;
}[] = [
  { key: "pendingAttendanceCount", label: "출석 미확정", to: "/teacher/attendance", unit: "건" },
  { key: "unwrittenLessonCount", label: "내용 미작성 수업", to: "/teacher/lessons", unit: "건" },
  { key: "unsignedStudentCount", label: "학생 미가입", to: "/teacher/students", unit: "명" },
  { key: "unlinkedParentCount", label: "학부모 미가입", to: "/teacher/students", unit: "명" },
];

/**
 * T-1 대시보드.
 *
 * <p><b>[할 일]과 [확인]을 분리한 이유가 있다.</b> 할 일은 0이 되면 흐려지지만,
 * 확인 두 항목은 <b>0이어도 숨기지 않는다</b> — 반 코드에는 전화번호 대조가 없어서
 * 선생님이 명단을 보는 것 자체가 방어 수단이다.
 */
export default function DashboardPage() {
  const dashboard = useQuery({ queryKey: ["teacher", "dashboard"], queryFn: getDashboard });

  if (dashboard.isPending || !dashboard.data) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }

  const { today, todo, stats } = dashboard.data;
  const date = new Date(today.date);
  const doneAll = TODO_ITEMS.every((item) => todo[item.key] === 0);

  return (
    <div className="space-y-4 md:max-w-2xl">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">
          {date.getFullYear()}년 {date.getMonth() + 1}월 {date.getDate()}일 (
          {DAY_LABELS[date.getDay() === 0 ? 7 : date.getDay()]})
        </h2>
        <p className="mt-0.5 text-sm text-slate-500">
          재원생 {stats.totalStudents}명 · 운영 중인 반 {stats.activeClassRooms}개
        </p>
      </div>

      {/*
        새 질문(2026-09-29). 선생님이 게시판을 마지막으로 연 뒤에 올라온 질문 수다.
        글마다 미답변 상태를 두지 않는다 — 게시판을 열면 0이 된다. 선생님은 푸시를 받지 않아서
        (15-4) 이 줄이 유일한 알림이다. 주황이 아니라 남색이다(주황은 자리가 정해져 있다).
      */}
      {(todo.newQuestionCount ?? 0) > 0 && (
        <Link
          to="/teacher/qna"
          className="flex items-center justify-between gap-2 rounded-xl bg-brand-900 px-4 py-3
                     text-sm font-medium text-white shadow-sm"
        >
          <span>새 질문 {todo.newQuestionCount}개가 올라왔어요</span>
          <span aria-hidden>→</span>
        </Link>
      )}

      <section>
        <h3 className="text-sm font-semibold text-slate-700">오늘 수업</h3>
        {today.lessons.length === 0 ? (
          <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
            오늘은 수업이 없습니다.
          </p>
        ) : (
          <ul className="mt-2 space-y-2">
            {today.lessons.map((lesson) => (
              <li
                key={lesson.lessonId}
                className="flex items-center justify-between gap-2 rounded-xl bg-white p-3
                           shadow-sm"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium text-slate-900">
                    {lesson.classRoomName}
                    {lesson.startTime && ` · ${lesson.startTime}`} · {lesson.studentCount}명
                  </p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {lesson.attendanceStatus === "PENDING" ? (
                      <Badge tone="warn">출석 미확정</Badge>
                    ) : (
                      <Badge tone="ok">출석 확정</Badge>
                    )}
                    {!lesson.contentWritten && <Badge tone="warn">내용 미작성</Badge>}
                  </div>
                </div>
                {/*
                  미확정이면 출석 화면으로 보낸다. 수업 상세에는 출석을 확정하는 기능이
                  없어서, 예전에는 "출석 확정하기"를 눌러도 확정할 수 없는 화면이 열렸다.
                */}
                <Link
                  to={
                    lesson.attendanceStatus === "PENDING"
                      ? `/teacher/attendance?lessonId=${lesson.lessonId}`
                      : `/teacher/lessons/${lesson.lessonId}`
                  }
                  className="shrink-0 rounded-lg bg-slate-900 px-3 py-2 text-xs font-medium
                             text-white"
                >
                  {lesson.attendanceStatus === "PENDING" ? "출석 확정하기" : "수업 열기"}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="text-sm font-semibold text-slate-700">할 일</h3>
        {doneAll ? (
          <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-600 shadow-sm">
            오늘 할 일을 모두 마쳤습니다.
          </p>
        ) : (
          <ul className="mt-2 divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm">
            {TODO_ITEMS.map((item) => {
              const count = todo[item.key];
              return (
                <li key={item.label}>
                  <Link
                    to={item.to}
                    className={`flex items-center justify-between px-3 py-3 text-sm ${
                      count === 0 ? "text-slate-300" : "text-slate-900"
                    }`}
                  >
                    <span>{item.label}</span>
                    <span className="flex items-center gap-1">
                      <span className={count === 0 ? "" : "font-semibold"}>
                        {count}
                        {item.unit}
                      </span>
                      <span className="text-slate-300">›</span>
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* 점검 항목. 0이어도 숨기지 않는다 — "확인했다"는 것 자체가 정보다 */}
      <section>
        <h3 className="text-sm font-semibold text-slate-700">확인</h3>
        <ul className="mt-2 divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm">
          <li>
            <Link
              to="/teacher/students?sort=recent"
              className="flex items-center justify-between px-3 py-3 text-sm text-slate-900"
            >
              <span>
                최근 7일 신규 가입
                <span className="mt-0.5 block text-xs text-slate-500">
                  모르는 이름이 있으면 삭제하세요
                </span>
              </span>
              <span className="flex items-center gap-1">
                <span className="font-semibold">{todo.recentSignupCount}명</span>
                <span className="text-slate-300">›</span>
              </span>
            </Link>
          </li>
          <li>
            <Link
              to="/teacher/class-rooms"
              className="flex items-center justify-between px-3 py-3 text-sm text-slate-900"
            >
              <span>
                가입 코드 열림
                <span className="mt-0.5 block text-xs text-slate-500">
                  등록 기간이 끝났으면 닫으세요
                </span>
              </span>
              <span className="flex items-center gap-1">
                {/* 코드를 열어둔 채 학기를 보내는 것이 가장 흔한 사고 경로다 */}
                {todo.openJoinCodeCount > 0 ? (
                  <Badge tone="warn">⚠ {todo.openJoinCodeCount}개</Badge>
                ) : (
                  <span className="font-semibold">0개</span>
                )}
                <span className="text-slate-300">›</span>
              </span>
            </Link>
          </li>
        </ul>
      </section>
    </div>
  );
}
