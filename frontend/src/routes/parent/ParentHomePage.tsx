import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { EXAM_TYPE_LABELS } from "../../shared/score/types";
import { getChildHome } from "./api";

/**
 * P-1 포털 홈. <b>호출은 하나</b>다 — 자녀를 바꾸면 이 쿼리만 다시 돈다.
 *
 * <p>메뉴는 네 개다. 수업영상·레포트, 수업 자료실, 수강 후기는 학부모 화면에서 제외됐다.
 * KW-Study(공부 시간·랭킹) 메뉴도 없다. 참고 디자인에 있더라도 넣지 마라.
 *
 * <p>값이 없는 카드는 숨긴다. 0을 표시하면 "시험이 오늘"이나 "출석 0회"로 읽힌다.
 */
export default function ParentHomePage() {
  const { children, selectedStudentId, setSelectedStudentId } = useSelectedChild();

  const home = useQuery({
    queryKey: ["parent", "home", selectedStudentId],
    queryFn: () => getChildHome(selectedStudentId!),
    enabled: selectedStudentId !== null,
  });

  if (home.isPending || !home.data) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }

  const {
    student,
    nextExam,
    nextLessonDate,
    notices,
    pendingHomeworkCount,
    nextClinic,
    thisMonthAttendance,
  } = home.data;

  return (
    <div className="space-y-4">
      <section className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-base font-semibold text-slate-900">
              {student.name} 학생, 환영합니다
            </p>
            <p className="mt-0.5 truncate text-sm text-slate-500">
              {student.classRooms.length > 0 ? student.classRooms.join(" · ") : "반 배정 전"}
            </p>
            {/* 서버가 마스킹한 값이다. 프론트에서 가리는 방식이면 개발자 도구에 원본이 남는다 */}
            {student.phone && <p className="text-xs text-slate-400">{student.phone}</p>}
          </div>
          {nextExam && (
            <div className="shrink-0 text-right">
              <p className="text-xs text-slate-500">{EXAM_TYPE_LABELS[nextExam.examType]}</p>
              <p className="text-lg font-bold text-slate-900">
                {nextExam.dDay === 0 ? "D-DAY" : `D-${nextExam.dDay}`}
              </p>
            </div>
          )}
        </div>

        {/* 자녀가 1명이면 드롭다운을 숨긴다 */}
        {children.length > 1 && (
          <select
            value={selectedStudentId ?? ""}
            onChange={(e) => setSelectedStudentId(Number(e.target.value))}
            className="mt-3 w-full rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
            aria-label="자녀 선택"
          >
            {children.map((child) => (
              <option key={child.studentId} value={child.studentId}>
                {child.name}
              </option>
            ))}
          </select>
        )}
      </section>

      <section className="rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-700">
            학원 공지 · 안내 ({notices.totalCount})
          </h3>
          <Link to="/parent/notices" className="text-xs text-slate-500 underline">
            전체 보기
          </Link>
        </div>
        {notices.recent.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">등록된 공지가 없습니다.</p>
        ) : (
          <ul className="mt-2 space-y-1.5">
            {notices.recent.map((notice) => (
              <li key={notice.noticeId} className="flex items-center gap-1.5">
                {notice.pinned && <Badge tone="warn">고정</Badge>}
                <span className="min-w-0 flex-1 truncate text-sm text-slate-800">
                  {notice.title}
                </span>
                <span className="shrink-0 text-xs text-slate-400">
                  {notice.publishedAt.slice(5, 10).replace("-", "/")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid grid-cols-2 gap-2">
        <InfoCard
          label="다음 수업"
          value={nextLessonDate ? nextLessonDate.slice(5).replace("-", "/") : null}
        />
        <InfoCard
          label="다음 클리닉"
          value={
            nextClinic
              ? `${nextClinic.clinicDate.slice(5).replace("-", "/")} ${nextClinic.startTime}`
              : null
          }
        />
        <InfoCard
          label="안 낸 숙제"
          value={pendingHomeworkCount > 0 ? `${pendingHomeworkCount}건` : "없음"}
          tone={pendingHomeworkCount > 0 ? "warn" : "ok"}
        />
        <InfoCard
          label="이번 달 출석"
          value={`출석 ${thisMonthAttendance.present} · 지각 ${thisMonthAttendance.late} · 결석 ${thisMonthAttendance.absent}`}
        />
      </div>

      {/* 메뉴 4개. 수업영상·자료실·후기는 학부모 화면에 없다 */}
      <nav className="grid grid-cols-2 gap-2">
        <MenuTile to="/parent/schedule" label="수업 · 클리닉 일정" />
        <MenuTile to="/parent/scores" label="테스트 결과" />
        <MenuTile to="/parent/homeworks" label="숙제 제출 현황" />
        <MenuTile to="/parent/me" label="내 정보" />
      </nav>
    </div>
  );
}

/** 값이 null이면 "미정"으로 둔다. 0으로 채우면 다른 뜻이 된다. */
function InfoCard({
  label,
  value,
  tone = "neutral",
}: {
  label: string;
  value: string | null;
  tone?: "neutral" | "warn" | "ok";
}) {
  const color =
    tone === "warn" ? "text-amber-700" : tone === "ok" ? "text-emerald-700" : "text-slate-900";
  return (
    <div className="rounded-xl bg-white p-3 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-0.5 text-sm font-medium ${value === null ? "text-slate-400" : color}`}>
        {value ?? "예정 없음"}
      </p>
    </div>
  );
}

function MenuTile({ to, label }: { to: string; label: string }) {
  return (
    <Link
      to={to}
      className="flex items-center justify-between rounded-xl bg-white p-4 text-sm font-medium
                 text-slate-900 shadow-sm"
    >
      {label}
      <span className="text-slate-300">›</span>
    </Link>
  );
}
