import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { HeroField } from "../../shared/components/HeroField";
import type { HeroStat } from "../../shared/components/HeroField";
import { NoticeCard } from "../../shared/components/NoticeCard";
import { SectionHead, TintBlock } from "../../shared/components/Section";
import { QuickRail } from "../../shared/components/QuickRail";
import { EXAM_TYPE_LABELS } from "../../shared/score/types";
import { todayLabel } from "../../shared/date";
import { getChildHome } from "./api";
import { attendedCount } from "../../shared/attendance/types";

/**
 * P-1 포털 홈. <b>호출은 하나</b>다 — 자녀를 바꾸면 이 쿼리만 다시 돈다.
 *
 * <p>메뉴는 여섯이다. 수업영상은 학부모 화면에서 제외됐다.
 * 수강 후기는 학생 전용이라 학부모 화면에 없다.
 * KW-Study(공부 시간·랭킹) 메뉴도 없다. 참고 디자인에 있더라도 넣지 마라.
 *
 * <p>값이 없는 칸은 <b>칸째로 숨긴다</b>. 0을 표시하면 "시험이 오늘"이나 "출석 0회"로 읽힌다.
 * 학생 홈(S-1)과 같은 언어를 쓴다 — 지면 + 숫자 칸 + 색 구획. 두 화면이 어긋나면
 * "아이 폰에는 다르게 나온다"는 문의가 된다.
 */
/** 홈을 뺀 학부모 화면 전부. 하단 탭 바에 있는 것도 빼지 않는다 — 레일은 전체 목록이다. */
const QUICK_ITEMS = (pendingHomework: number, noticeCount: number) => [
  {
    to: "/parent/schedule",
    icon: "calendar" as const,
    label: "일정 · 출석",
    primary: true,
  },
  { to: "/parent/homeworks", icon: "homework" as const, label: "숙제", count: pendingHomework },
  { to: "/parent/scores", icon: "chart" as const, label: "성적" },
  { to: "/parent/lessons", icon: "book" as const, label: "주간 레포트" },
  { to: "/parent/notices", icon: "megaphone" as const, label: "공지", count: noticeCount },
  { to: "/parent/me", icon: "user" as const, label: "내 정보" },
];

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
    nextLessonTime,
    nextLessonDDay,
    notices,
    pendingHomeworkCount,
    nextClinic,
    thisMonthAttendance,
  } = home.data;

  const dDayLabel = (dDay: number) => (dDay === 0 ? "오늘" : `D-${dDay}`);
  const dateLabel = (date: string, time?: string | null) =>
    `${date.slice(5).replace("-", "/")}${time ? ` ${time}` : ""}`;

  const clinicRow = nextClinic
    ? {
        label: "다음 클리닉",
        value: dDayLabel(nextClinic.dDay),
        sub: `${dateLabel(nextClinic.clinicDate)} ${nextClinic.arrivalTime} 도착`,
      }
    : undefined;

  const stats: HeroStat[] = [];
  if (nextLessonDate && nextLessonDDay !== null) {
    stats.push({
      label: "다음 수업",
      value: dDayLabel(nextLessonDDay),
      sub: dateLabel(nextLessonDate, nextLessonTime),
      extra: clinicRow,
    });
  } else if (clinicRow) {
    stats.push(clinicRow);
  }
  stats.push({
    label: "미완료 숙제",
    value: `${pendingHomeworkCount}`,
    sub: pendingHomeworkCount > 0 ? "확인 필요" : "다 냈어요",
    hot: pendingHomeworkCount > 0,
  });
  if (nextExam) {
    stats.push({
      label: EXAM_TYPE_LABELS[nextExam.examType],
      /* 수업·클리닉 칸과 같은 함수다. 시험 당일만 "D-0"이면 같은 지면에 "오늘"과 섞인다 */
      value: dDayLabel(nextExam.dDay),
      sub: `${nextExam.startDate.slice(5).replace("-", ".")} 시작`,
    });
  }

  return (
    <div>
      <HeroField
        eyebrow={todayLabel()}
        title={
          <>
            <span className="text-accent-300">{student.name}</span> 학생
            <br />
            학부모님, 환영합니다
          </>
        }
        stats={stats}
      >
        {/*
          자녀가 1명이면 드롭다운을 숨긴다. 지면 안에 두는 이유는, 이걸 바꾸면 아래
          화면 전체가 다른 아이 것으로 바뀌기 때문이다 — 카드 하나의 설정이 아니다.
        */}
        {children.length > 1 && (
          <div className="mt-4 border-t border-white/10 pt-3.5">
            <label
              htmlFor="parent-child-select"
              className="text-[11px] font-semibold tracking-[0.1em] text-brand-200/70"
            >
              자녀 선택
            </label>
            <select
              id="parent-child-select"
              value={selectedStudentId ?? ""}
              onChange={(e) => setSelectedStudentId(Number(e.target.value))}
              className="mt-1.5 w-full rounded-xl border border-white/15 bg-white/10 px-3 py-2.5
                         text-base text-white outline-none transition-colors
                         focus:border-white/40 focus:bg-white/15"
            >
              {children.map((child) => (
                /* 남색 위 select라 옵션 목록은 OS가 그린다 — 글자색을 되돌려 놔야 안 보인다 */
                <option key={child.studentId} value={child.studentId} className="text-brand-900">
                  {child.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </HeroField>

      <section className="hero-lift card p-4">
        <QuickRail items={QUICK_ITEMS(pendingHomeworkCount, notices.totalCount)} />
      </section>

      {/*
        학부모 화면의 본론. 학생 홈과 달리 목록이 아니라 개수 하나라, 블록 한 줄로 끝난다.
        안 낸 게 없으면 회색으로 내려앉는다 — 주황은 처리할 게 있을 때만이다.
      */}
      <section className="mt-5">
        <SectionHead
          tone={pendingHomeworkCount > 0 ? "accent" : "neutral"}
          title="숙제"
          count={pendingHomeworkCount}
          to="/parent/homeworks"
        />
        <TintBlock tone={pendingHomeworkCount > 0 ? "accent" : "neutral"}>
          <div className="flex items-center gap-3 px-3.5 py-3.5">
            <p className="min-w-0 flex-1 text-[14px] font-bold text-brand-900">
              {pendingHomeworkCount > 0
                ? `미완료 숙제가 ${pendingHomeworkCount}건 있습니다`
                : "미완료 숙제가 없습니다"}
            </p>
          </div>
        </TintBlock>
      </section>

      {/* 시험 범위는 D-day와 같은 시험 이야기라 떨어뜨리지 않는다. S-1과 같은 블록이다 */}
      {nextExam && (
        <section className="mt-5">
          <SectionHead tone="brand" title="시험 일정" />
          <TintBlock tone="brand">
            <div className="px-3.5 py-3.5">
              <p className="text-[14px] font-bold text-brand-900">
                {EXAM_TYPE_LABELS[nextExam.examType]}
                <span className="tnum ml-2 text-[11.5px] font-medium text-brand-600/80">
                  {nextExam.startDate.replace(/-/g, ".")} 시작
                </span>
              </p>
              <p
                className={`mt-1.5 whitespace-pre-wrap text-[12.5px] leading-relaxed ${
                  nextExam.scopeNote ? "text-brand-950/80" : "text-brand-600/50"
                }`}
              >
                {nextExam.scopeNote ?? "시험 범위 미등록"}
              </p>
            </div>
          </TintBlock>
        </section>
      )}

      <section className="mt-5">
        <SectionHead tone="brand" title="이번 달 출석" to="/parent/schedule" />
        <TintBlock tone="brand">
          <div className="grid grid-cols-3 divide-x divide-brand-100">
            {/* 대체 등원(makeup)도 출석이다(CLAUDE.md 5-1). 서버의 present는 makeup을
                포함하지 않아서, present만 쓰면 대체 등원한 날이 세 칸 어디에도 안 잡힌다 */}
            <AttendanceCell
              label="출석"
              value={attendedCount(thisMonthAttendance)}
            />
            <AttendanceCell label="지각" value={thisMonthAttendance.late} />
            <AttendanceCell label="결석" value={thisMonthAttendance.absent} />
          </div>
        </TintBlock>
      </section>

      <div className="mt-5">
        <NoticeCard totalCount={notices.totalCount} recent={notices.recent} to="/parent/notices" />
      </div>
    </div>
  );
}

/**
 * 출석·지각·결석 한 칸. <b>0을 숨기지 않는다</b> — 여기서 0은 "값이 없다"가 아니라
 * "결석이 없다"는 좋은 소식이고, 칸이 사라지면 셋이 나란히 서던 격자가 무너진다.
 * 홈 지면의 숫자 칸이 값 없는 항목을 빼는 것과는 반대 방향이다.
 */
function AttendanceCell({ label, value }: { label: string; value: number }) {
  return (
    <div className="px-3 py-3 text-center">
      <p className="text-[11px] font-semibold text-brand-600/70">{label}</p>
      <p className="tnum mt-1 text-[20px] font-extrabold tracking-[-0.03em] text-brand-900">
        {value}
      </p>
    </div>
  );
}
