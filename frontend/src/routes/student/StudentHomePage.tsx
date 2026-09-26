import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { HeroField } from "../../shared/components/HeroField";
import type { HeroStat } from "../../shared/components/HeroField";
import { NoticeCard } from "../../shared/components/NoticeCard";
import { SectionHead, TintBlock } from "../../shared/components/Section";
import { LastLessonCard } from "./LastLessonCard";
import { QuickRail } from "../../shared/components/QuickRail";
import { EXAM_TYPE_LABELS } from "../../shared/score/types";
import { todayLabel } from "../../shared/date";
import { formatDueAt, remainingLabel } from "../../shared/homework/types";
import { getStudentHome } from "./api";

/**
 * S-1 학생 홈. 호출은 하나다.
 *
 * <p><b>미완료 숙제가 화면에서 가장 크다.</b> 학생이 서비스를 여는 이유가
 * "뭘 해야 하는지" 확인하는 것이라, 이걸 아래로 내리면 화면의 목적이 사라진다.
 * 지면의 주황 칸과 아래 주황 구획이 같은 것을 두 번 말하는 건 의도다 —
 * 위는 개수, 아래는 무엇인지다.
 *
 * <p>마감이 지난 미제출도 그대로 남는다. 사라지면 학생이 잊는다.
 */
/**
 * 홈을 뺀 학생 화면 전부. 순서는 학생이 여는 빈도순이고, 하단 탭 바에 있는 것도 빼지 않는다.
 *
 * <p>점 배지는 숫자를 쓰지 않는다. 52px 칸 위의 "3"은 읽으려고 눈이 멈추는데,
 * 여기서 알아야 하는 건 개수가 아니라 "볼 게 있다"뿐이다.
 *
 * <p>숙제만 primary(남색)다. 학생이 이 화면에서 갈 곳이 하나면 거기다.
 */
const QUICK_ITEMS = (pendingHomework: number, noticeCount: number) => [
  {
    to: "/student/homeworks",
    icon: "homework" as const,
    label: "숙제",
    count: pendingHomework,
    primary: true,
  },
  { to: "/student/lessons", icon: "video" as const, label: "수업" },
  { to: "/student/clinics", icon: "clock" as const, label: "스케줄" },
  { to: "/student/scores", icon: "chart" as const, label: "성적" },
  { to: "/student/online-tests", icon: "test" as const, label: "테스트" },
  { to: "/student/attendances", icon: "calendar" as const, label: "출석" },
  { to: "/student/notices", icon: "megaphone" as const, label: "공지", count: noticeCount },
  { to: "/student/qna", icon: "question" as const, label: "질문" },
];

export default function StudentHomePage() {
  const home = useQuery({ queryKey: ["student", "home"], queryFn: getStudentHome });

  if (home.isPending || !home.data) {
    return <p className="hero-lift card p-6 text-sm text-slate-400">불러오는 중…</p>;
  }

  const { student, nextLesson, nextExam, nextClinic, currentHomeworks, lastLesson, notices }
    = home.data;

  const pending = currentHomeworks.length;

  /* "D-2" · "오늘". dDay가 음수인 값은 서버가 안 내려준다 */
  const dDayLabel = (dDay: number) => (dDay === 0 ? "오늘" : `D-${dDay}`);
  /* "09/12 14:00" — 연도를 뺀다. 360px에서 잘린다 */
  const dateLabel = (date: string, time?: string | null) =>
    `${date.slice(5).replace("-", "/")}${time ? ` ${time}` : ""}`;

  const clinicRow = nextClinic
    ? {
        label: "다음 클리닉",
        value: dDayLabel(nextClinic.dDay),
        sub: `${dateLabel(nextClinic.clinicDate)} ${nextClinic.arrivalTime} 도착`,
      }
    : undefined;

  /*
    지면의 숫자 칸. 값이 없는 건 "미정"으로 채우지 않고 <b>칸째로 뺀다</b> —
    없는 시험에 D-0을 넣으면 시험이 오늘로 읽힌다. 그래서 길이가 1~3으로 변한다.

    미완료 숙제만 항상 있다. 0도 뜻이 있는 값이라서다("다 냈다"). 대신 0일 때는
    주황을 끈다 — 처리할 게 없는데 주황이면 그 색이 뜻을 잃는다.
  */
  const stats: HeroStat[] = [];
  if (nextLesson) {
    stats.push({
      label: "다음 수업",
      value: dDayLabel(nextLesson.dDay),
      sub: dateLabel(nextLesson.lessonDate, nextLesson.startTime),
      /* 같은 종류의 일정이라 한 칸에 묶는다. 칸을 넷으로 늘리면 360px에서 깨진다 */
      extra: clinicRow,
    });
  } else if (clinicRow) {
    /* 수업이 없는데 클리닉만 있으면 클리닉이 그 칸의 주인이 된다.
       extra만 있는 칸을 만들면 첫 줄이 비어 칸이 깨진다 */
    stats.push(clinicRow);
  }
  stats.push({
    label: "미완료 숙제",
    value: `${pending}`,
    sub: pending > 0 ? "확인하세요" : "다 냈어요",
    hot: pending > 0,
  });
  if (nextExam) {
    stats.push({
      label: EXAM_TYPE_LABELS[nextExam.examType],
      /* 수업·클리닉 칸과 같은 함수다. 시험 당일만 "D-0"이면 같은 지면에 "오늘"과 섞인다 */
      value: dDayLabel(nextExam.dDay),
      /* 연도를 뺀다. 360px에서 "2026.10.12 시작"은 잘려서 "2026.10.12 시…"가 된다 */
      sub: `${nextExam.startDate.slice(5).replace("-", ".")} 시작`,
    });
  }

  return (
    <div>
      <HeroField
        eyebrow={todayLabel()}
        title={
          <>
            {/* 이름 한 곳만 주황이다. 장식용 세 곳 중 하나 — tailwind.config의 accent 주석 */}
            <span className="text-accent-300">{student.name}</span> 학생,
            <br />
            {pending > 0 ? `미완료 숙제가 ${pending}개 있어요` : "미완료 숙제가 없어요"}
          </>
        }
        stats={stats}
      />

      {/*
        홈을 뺀 모든 화면이 여기 다 있다. 하단 탭 바와 겹치는 건 의도다 —
        아래 바는 매일 쓰는 다섯 곳의 빠른 길이고, 이 레일은 "전부 한눈에"가 목적이다.

        hero-lift로 지면 아래끝에 걸터앉는다. 지면의 pb-16이 그 자리다.
        카드 안쪽 여백을 음수 마진으로 뚫고 나가야(-mx-4) 마지막 칸이 카드 끝에서 잘린다.
        그 잘린 칸이 "옆으로 넘길 수 있다"를 말하는 유일한 신호다.
      */}
      <section className="hero-lift card p-4">
        <QuickRail items={QUICK_ITEMS(pending, notices.totalCount)} />
      </section>

      {/* 홈의 본론. 안 낸 게 없을 때만 자리를 내준다 */}
      <section className="mt-5">
        <SectionHead
          tone="accent"
          title="미완료 숙제"
          count={pending}
          to="/student/homeworks"
        />
        {pending === 0 ? (
          <TintBlock tone="neutral">
            <p className="px-4 py-6 text-center text-sm text-slate-500">
              미완료 숙제가 없습니다. 잘하고 있어요.
            </p>
          </TintBlock>
        ) : (
          <TintBlock tone="accent">
            {currentHomeworks.map((homework) => (
              <Link
                key={homework.homeworkId}
                to={`/student/homeworks/${homework.homeworkId}`}
                className="flex items-center gap-3 px-3.5 py-3.5 transition-colors
                           active:bg-accent-100/60"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[15px] font-bold tracking-[-0.015em]
                                   text-brand-900">
                    {homework.title}
                  </span>
                  <span className="tnum mt-0.5 block text-[11.5px] text-accent-700/75">
                    {formatDueAt(homework.dueAt)} 마감
                  </span>
                </span>
                {/*
                  주황 면 위에서는 옅은 배지가 배경에 묻힌다. 흰 바탕에 주황 테두리로
                  뒤집어야 읽힌다. 마감이 지난 것도 빨강으로 올리지 않는다 —
                  빨강은 이 앱에서 "결석·위험"만 뜻하고, 미완료 숙제는 위험이 아니다.
                */}
                <span
                  className="tnum shrink-0 rounded-lg border border-accent-200 bg-white px-2 py-1
                             text-[10.5px] font-bold text-accent-600"
                >
                  {remainingLabel(homework.remainingMinutes)}
                </span>
              </Link>
            ))}
          </TintBlock>
        )}
      </section>

      {/*
        시험 범위는 D-day와 같은 시험 이야기라 떨어뜨리지 않는다. 예전에 범위가 화면 맨
        아래 별도 카드에 있어서, D-61을 보고 범위를 알려면 끝까지 내려야 했다.
        남은 날짜는 위 지면 칸이 이미 말했으므로 여기서 다시 세지 않는다 — 같은 숫자를
        두 번 쓰면 어느 쪽이 맞는지 확인하려고 눈이 왕복한다.
      */}
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
              {/* 선생님이 아직 안 올렸으면 null이다. "미정"이라고 지어내지 마라 */}
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

      <div className="mt-5">
        <NoticeCard
          totalCount={notices.totalCount}
          recent={notices.recent}
          to="/student/notices"
        />
      </div>

      {/* 지난 수업이 통째로 비어 있으면(아무것도 안 적힌 수업만 있으면) null이라 숨긴다 */}
      {lastLesson && (
        <section className="mt-5">
          <SectionHead tone="neutral" title="지난 수업" to="/student/lessons" />
          <LastLessonCard lesson={lastLesson} />
        </section>
      )}
    </div>
  );
}
