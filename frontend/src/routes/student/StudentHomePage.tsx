import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { DdayPill } from "../../shared/components/DdayPill";
import { MenuTile } from "../../shared/components/MenuTile";
import { formatDueAt, remainingLabel } from "../../shared/homework/types";
import { EXAM_TYPE_LABELS } from "../../shared/score/types";
import { getStudentHome } from "./api";

/**
 * S-1 학생 홈. 호출은 하나다.
 *
 * <p><b>미완료 숙제가 화면에서 가장 크다.</b> 학생이 서비스를 여는 이유가
 * "뭘 해야 하는지" 확인하는 것이라, 이걸 아래로 내리면 화면의 목적이 사라진다.
 *
 * <p>마감이 지난 미제출도 그대로 남는다. 사라지면 학생이 잊는다.
 */
export default function StudentHomePage() {
  const home = useQuery({ queryKey: ["student", "home"], queryFn: getStudentHome });

  if (home.isPending || !home.data) {
    return <p className="hero-lift card p-6 text-sm text-slate-400">불러오는 중…</p>;
  }

  const { student, nextLesson, nextExam, currentHomeworks, noticeCount } = home.data;

  return (
    <div className="space-y-5">
      {/* 남색 띠에 걸쳐 앉는 카드. hero-lift가 이 디자인의 시그니처다 */}
      <section className="hero-lift card p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-lg font-bold tracking-[-0.01em] text-brand-900">
              {student.name} 학생, 환영합니다
            </h2>
            {/*
              반 이름을 같은 줄에 이어 붙이면 한 문장이 너무 길어져 "D-4"가 다음 줄로
              혼자 넘어간다. 날짜·남은 일수는 한 줄, 반 이름은 칩으로 내린다.
            */}
            {nextLesson && (
              <>
                {/*
                  시각은 반의 요일 슬롯에서 온다. 슬롯이 없으면 null이라 날짜만 그린다 —
                  없는 시각을 지어내면 학생이 그 시각에 맞춰 온다.
                */}
                <p className="tnum mt-1 text-sm text-slate-500">
                  다음 수업 {nextLesson.lessonDate.slice(5).replace("-", "/")}
                  {nextLesson.startTime && ` ${nextLesson.startTime}`}
                  {nextLesson.dDay === 0 ? " · 오늘" : ` · D-${nextLesson.dDay}`}
                </p>
                <span
                  className="mt-2 inline-block rounded-md bg-brand-50 px-2 py-1 text-xs
                             font-medium text-brand-700"
                >
                  {nextLesson.classRoomName}
                </span>
              </>
            )}
          </div>
          {/* 시험 일정이 없으면 알약 자체를 숨긴다. D-0을 보여주면 시험이 오늘로 읽힌다 */}
          {nextExam && (
            <DdayPill label={EXAM_TYPE_LABELS[nextExam.examType]} dDay={nextExam.dDay} />
          )}
        </div>
      </section>

      {/* 홈의 주인공. 미제출이 없을 때만 자리를 내준다 */}
      <section>
        {/* 화면의 본론이다. 회색 작은 라벨로 두면 구획째로 곁다리가 된다 */}
        <h3 className="section-title px-1">미완료 숙제</h3>
        {currentHomeworks.length === 0 ? (
          <p className="card mt-2 p-6 text-center text-sm text-slate-500">
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
                    className={`card block p-4 transition-transform active:scale-[0.99] ${
                      overdue ? "ring-1 ring-inset ring-red-200" : ""
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="text-base font-bold text-brand-900">{homework.title}</span>
                      <Badge tone={overdue ? "danger" : "warn"}>
                        {remainingLabel(homework.remainingMinutes)}
                      </Badge>
                    </div>
                    <p className="tnum mt-1 text-sm text-slate-500">
                      {formatDueAt(homework.dueAt)} 마감
                    </p>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {/* 남은 날짜는 위 알약이 이미 말했다. 여기는 범위만 — 같은 숫자를 두 번 쓰지 않는다 */}
      {nextExam?.scopeNote && (
        <section className="card p-4">
          <h3 className="section-title">{EXAM_TYPE_LABELS[nextExam.examType]} 범위</h3>
          <p className="tnum mt-0.5 text-xs text-slate-500">
            {nextExam.startDate.replace(/-/g, ".")} 시작
          </p>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
            {nextExam.scopeNote}
          </p>
        </section>
      )}

      <nav className="grid grid-cols-2 gap-2.5">
        <MenuTile to="/student/homeworks" icon="homework" label="숙제" sub="제출하고 피드백 확인" />
        <MenuTile
          to="/student/lessons"
          icon="video"
          label="수업영상 · 레포트"
          sub="날짜별 영상과 수업 내용"
        />
        {/*
          피드백 타일은 링크가 숙제 타일과 같은 곳이라 격자에서 한 칸을 두 번 쓰고 있었다.
          성적은 격자에 아예 없어서 상단 탭의 "내 정보"로만 닿았다. 그 자리를 성적에 준다.
        */}
        <MenuTile to="/student/scores" icon="chart" label="테스트 결과" sub="주차별 성적 확인" />
        <MenuTile
          to="/student/materials"
          icon="folder"
          label="수업 자료실"
          sub="학습지 · 교재 내려받기"
        />
        <MenuTile
          to="/student/online-tests"
          icon="test"
          label="온라인 테스트"
          sub="답 입력하고 결과 확인"
        />
        <MenuTile
          to="/student/attendances"
          icon="calendar"
          label="출석 현황"
          sub="월별 출석 캘린더"
        />
        <MenuTile to="/student/clinics" icon="clock" label="클리닉 신청" sub="보충 수업 신청·변경" />
        <MenuTile
          to="/student/notices"
          icon="megaphone"
          label="학원 공지"
          sub="안내 사항 모아보기"
          badge={noticeCount > 0 ? String(noticeCount) : null}
        />
      </nav>
    </div>
  );
}
