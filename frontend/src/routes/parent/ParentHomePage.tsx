import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { DdayPill } from "../../shared/components/DdayPill";
import { MenuTile } from "../../shared/components/MenuTile";
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
    return <p className="hero-lift card p-6 text-sm text-slate-400">불러오는 중…</p>;
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
    <div className="space-y-5">
      {/* 남색 띠에 걸쳐 앉는 카드. hero-lift가 이 디자인의 시그니처다 */}
      <section className="hero-lift card p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-lg font-bold tracking-[-0.01em] text-brand-900">
              {student.name} 학생, 환영합니다
            </p>
            <p className="mt-0.5 truncate text-sm text-slate-500">
              {student.classRooms.length > 0 ? student.classRooms.join(" · ") : "반 배정 전"}
            </p>
            {/* 서버가 마스킹한 값이다. 프론트에서 가리는 방식이면 개발자 도구에 원본이 남는다 */}
            {student.phone && <p className="tnum mt-0.5 text-xs text-slate-400">{student.phone}</p>}
          </div>
          {nextExam && (
            <DdayPill label={EXAM_TYPE_LABELS[nextExam.examType]} dDay={nextExam.dDay} />
          )}
        </div>

        {/* 자녀가 1명이면 드롭다운을 숨긴다 */}
        {children.length > 1 && (
          <div className="mt-4 border-t border-slate-100 pt-3">
            <span className="eyebrow">자녀 선택</span>
            <select
              value={selectedStudentId ?? ""}
              onChange={(e) => setSelectedStudentId(Number(e.target.value))}
              className="mt-1.5 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5
                         text-base text-slate-900 outline-none transition-colors
                         focus:border-brand-600 focus:ring-4 focus:ring-brand-600/15"
              aria-label="자녀 선택"
            >
              {children.map((child) => (
                <option key={child.studentId} value={child.studentId}>
                  {child.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </section>

      {/*
        공지만 따뜻한 색이다. 남색 화면에서 이 카드 하나가 튀어서
        "새로 읽을 게 있다"는 신호가 된다. 참고 디자인의 노란 공지 띠 자리다.
      */}
      <section className="rounded-2xl bg-amber-50 p-4 ring-1 ring-inset ring-amber-200">
        <div className="flex items-center justify-between gap-2">
          <h3 className="text-sm font-bold text-amber-900">
            학원 공지 · 안내 <span className="tnum">({notices.totalCount})</span>
          </h3>
          <Link to="/parent/notices" className="text-xs font-medium text-amber-800 underline">
            전체 보기
          </Link>
        </div>
        {notices.recent.length === 0 ? (
          <p className="mt-2 text-sm text-amber-800/70">등록된 공지가 없습니다.</p>
        ) : (
          <ul className="mt-2.5 space-y-1.5">
            {notices.recent.map((notice) => (
              <li key={notice.noticeId} className="flex items-center gap-1.5">
                {notice.pinned && <Badge tone="warn">고정</Badge>}
                <span className="min-w-0 flex-1 truncate text-sm text-amber-950">
                  {notice.title}
                </span>
                <span className="tnum shrink-0 text-xs text-amber-700/70">
                  {notice.publishedAt.slice(5, 10).replace("-", "/")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid grid-cols-2 gap-2.5">
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
      <nav className="grid grid-cols-2 gap-2.5">
        <MenuTile
          to="/parent/schedule"
          icon="calendar"
          label="수업 · 클리닉 일정"
          sub="월별 출석 캘린더"
        />
        <MenuTile to="/parent/scores" icon="chart" label="테스트 결과" sub="시험별 점수 확인" />
        <MenuTile
          to="/parent/homeworks"
          icon="homework"
          label="숙제 제출 현황"
          sub="제출·미제출 내역"
        />
        <MenuTile to="/parent/me" icon="user" label="내 정보" sub="연락처·비밀번호 변경" />
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
    tone === "warn" ? "text-amber-700" : tone === "ok" ? "text-emerald-700" : "text-brand-900";
  return (
    <div className="card p-3.5">
      <p className="eyebrow">{label}</p>
      <p
        className={`tnum mt-1.5 text-sm font-semibold ${
          value === null ? "text-slate-400" : color
        }`}
      >
        {value ?? "예정 없음"}
      </p>
    </div>
  );
}
