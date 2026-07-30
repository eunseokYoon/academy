import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { formatDueAt, remainingLabel } from "../../shared/homework/types";
import { EXAM_TYPE_LABELS } from "../../shared/score/types";
import { getStudentHome } from "./api";

/**
 * S-1 학생 홈. 호출은 하나다.
 *
 * <p><b>지금 할 숙제가 화면에서 가장 크다.</b> 학생이 서비스를 여는 이유가
 * "뭘 해야 하는지" 확인하는 것이라, 이걸 아래로 내리면 화면의 목적이 사라진다.
 *
 * <p>마감이 지난 미제출도 그대로 남는다. 사라지면 학생이 잊는다.
 */
export default function StudentHomePage() {
  const home = useQuery({ queryKey: ["student", "home"], queryFn: getStudentHome });

  if (home.isPending || !home.data) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }

  const { student, nextLesson, nextExam, currentHomeworks, unreadFeedbackCount, noticeCount } =
    home.data;

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">{student.name} 학생, 환영합니다</h2>
        {nextLesson && (
          <p className="mt-0.5 text-sm text-slate-500">
            다음 수업 {nextLesson.lessonDate.slice(5).replace("-", "/")} ·{" "}
            {nextLesson.classRoomName}
            {nextLesson.dDay === 0 ? " · 오늘" : ` · D-${nextLesson.dDay}`}
          </p>
        )}
      </div>

      {/* 홈의 주인공. 미제출이 없을 때만 자리를 내준다 */}
      <section>
        <h3 className="text-sm font-semibold text-slate-700">지금 할 숙제</h3>
        {currentHomeworks.length === 0 ? (
          <p className="mt-2 rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
            안 낸 숙제가 없습니다. 잘하고 있어요.
          </p>
        ) : (
          <ul className="mt-2 space-y-2">
            {currentHomeworks.map((homework) => {
              const overdue = homework.remainingMinutes < 0;
              return (
                <li key={homework.homeworkId}>
                  <Link
                    to={`/student/homeworks/${homework.homeworkId}`}
                    className={`block rounded-xl p-4 shadow-sm ${
                      overdue ? "bg-red-50" : "bg-white"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="text-base font-semibold text-slate-900">
                        {homework.title}
                      </span>
                      <Badge tone={overdue ? "danger" : "warn"}>
                        {remainingLabel(homework.remainingMinutes)}
                      </Badge>
                    </div>
                    <p className="mt-1 text-sm text-slate-500">
                      {formatDueAt(homework.dueAt)} 마감
                    </p>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* 시험 일정이 등록되지 않았으면 카드 자체를 숨긴다. 0을 보여주면 시험이 오늘로 읽힌다 */}
      {nextExam && (
        <section className="rounded-xl bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {EXAM_TYPE_LABELS[nextExam.examType]}
              </p>
              <p className="text-xs text-slate-500">
                {nextExam.startDate.replace(/-/g, ".")} 시작
              </p>
            </div>
            <span className="text-xl font-bold text-slate-900">
              {nextExam.dDay === 0 ? "D-DAY" : `D-${nextExam.dDay}`}
            </span>
          </div>
          {nextExam.scopeNote && (
            <p className="mt-2 whitespace-pre-wrap text-xs text-slate-600">{nextExam.scopeNote}</p>
          )}
        </section>
      )}

      <nav className="grid grid-cols-2 gap-2">
        <HomeTile to="/student/homeworks" label="숙제" badge={null} />
        <HomeTile to="/student/lessons" label="수업영상 · 레포트" badge={null} />
        <HomeTile
          to="/student/homeworks"
          label="선생님 피드백"
          badge={unreadFeedbackCount > 0 ? `새 ${unreadFeedbackCount}` : null}
        />
        <HomeTile to="/student/materials" label="수업 자료실" badge={null} />
        <HomeTile to="/student/online-tests" label="온라인 테스트" badge={null} />
        <HomeTile to="/student/attendances" label="출석 현황" badge={null} />
        <HomeTile to="/student/clinics" label="클리닉 신청" badge={null} />
        <HomeTile
          to="/student/notices"
          label="학원 공지"
          badge={noticeCount > 0 ? String(noticeCount) : null}
        />
      </nav>
    </div>
  );
}

function HomeTile({
  to,
  label,
  badge,
}: {
  to: string;
  label: string;
  badge: string | null;
}) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between gap-1 rounded-xl bg-white p-4 text-sm
                 font-medium text-slate-900 shadow-sm"
    >
      {label}
      {badge && <Badge tone="warn">{badge}</Badge>}
    </Link>
  );
}
