import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { LessonDayFilter } from "../../shared/components/LessonDayFilter";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { groupByLessonDay } from "../../shared/homework/lessonDay";
import { SUBMISSION_LABELS, remainingLabel } from "../../shared/homework/types";
import { listMyHomeworks, listMyHomeworkNotes } from "./api";
import type { StudentHomeworkListItem } from "./api";

const NOW = new Date();

/*
  "지금 낼 수 있고, 내야 하는 것"의 정의다. 축이 둘이라 한 줄로 못 쓴다 —
  GRID는 선생님이 재제출을 열어 줘야만 낼 수 있고(status는 ⭕를 받아도
  NOT_SUBMITTED로 남는다), ONLINE은 아직 안 낸 것이 그대로 할 일이다.
  CLAUDE.md의 4-2·4-3이 근거다. 여기서 status로 GRID를 판정하지 마라.
*/
const isTodo = (item: StudentHomeworkListItem) =>
  item.kind === "GRID" ? item.resubmitRequired : item.status === "NOT_SUBMITTED";

/**
 * S-2 숙제 목록. 미제출이면서 마감이 가까운 것이 위로 온다(서버 정렬).
 *
 * <p>남은 시간은 서버가 계산해 내려준 값이다. 기기 시계가 틀려도 같은 값이 보인다.
 *
 * <p><b>두 덩어리로 나눈다.</b> 예전에는 흰 카드 여섯 장이 세로로 쌓여서, "지금 내야 하는
 * 것"과 "이미 끝난 것"이 같은 무게로 보였다 — 학생이 목록을 처음부터 끝까지 읽어야
 * 할 일을 찾을 수 있었다는 뜻이다. 위 덩어리만 주황이다.
 *
 * <p>아래 덩어리는 다시 <b>수업일별</b>로 나뉜다. 날짜는 그룹 머리로 올라가므로 줄 안에는
 * 반 이름만 남는다.
 */
export default function StudentHomeworkPage() {
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState<number | "">("");
  const [day, setDay] = useState<string | null | undefined>(undefined);
  const [detail, setDetail] = useState<StudentHomeworkListItem | null>(null);

  /*
    쿼리가 둘인 이유. 위 덩어리는 달 필터를 <b>따르지 않는다</b> — 8월을 보는 동안
    7월 미제출이 숨으면 "지금 낼 것"이라는 이름이 거짓이 된다. 놓치지 말라고 만든
    덩어리라 필터로 가려지면 존재 이유가 없다.

    달을 안 고른 상태에서는 두 파라미터가 같아서 캐시 키도 같다 — 요청은 한 번만 나간다.
    year를 홀로 보내지 않는 이유가 이것이다(서버도 연·월이 다 있어야 범위를 건다).
  */
  const todoParams = {};
  const todoQuery = useQuery({
    queryKey: ["student", "homeworks", todoParams],
    queryFn: () => listMyHomeworks(todoParams),
  });

  /*
    글 목록은 달 필터를 따르지 않는다 — 「이번 주에 낼 것」이라는 이름이 거짓이 된다.
    todoQuery와 같은 이유다.
  */
  const notesQuery = useQuery({
    queryKey: ["student", "homework-notes"],
    queryFn: listMyHomeworkNotes,
  });

  const listParams = month === "" ? todoParams : { year, month };
  const listQuery = useQuery({
    queryKey: ["student", "homeworks", listParams],
    queryFn: () => listMyHomeworks(listParams),
  });

  function changeMonth(value: number | "") {
    setMonth(value);
    setDay(undefined);
  }

  function changeYear(value: number) {
    setYear(value);
    setDay(undefined);
  }

  if (listQuery.isPending) {
    return (
      <>
        <PageTitle>숙제</PageTitle>
        <p className="text-sm text-slate-400">불러오는 중…</p>
      </>
    );
  }

  const todo = (todoQuery.data?.items ?? []).filter(isTodo);

  /*
    지난 숙제만 날짜로 묶는다. 할 일은 위 주황 덩어리에 이미 있어서, 여기 또 넣으면
    같은 줄이 화면에 두 번 나온다.
  */
  const groups = groupByLessonDay((listQuery.data?.items ?? []).filter((item) => !isTodo(item)));
  const shown = day === undefined ? groups : groups.filter((group) => group.lessonDate === day);

  return (
    <>
      <PageTitle>숙제</PageTitle>

      <div className="mb-4">
        <LessonDayFilter
          year={year}
          month={month}
          selectedDay={day}
          groups={groups}
          onYearChange={changeYear}
          onMonthChange={changeMonth}
          onDayChange={setDay}
        />
      </div>

      {(notesQuery.data ?? []).length > 0 && (
        <section className="mb-5">
          <SectionHead tone="accent" title="이번 주에 낼 것" />
          <TintBlock tone="accent">
            {(notesQuery.data ?? []).map((note) => (
              <div key={note.lessonId} className="px-3.5 py-3.5">
                {/*
                  반 이름과 주차가 먼저다. 여러 반에 속한 학생은 어느 반 숙제인지
                  모르면 글을 읽어도 쓸 수 없다.
                */}
                <p className="tnum text-[11.5px] font-semibold text-accent-700/75">
                  {note.classRoomName} · {note.weekLabel} · {note.lessonDate} 수업
                </p>
                {/* 줄바꿈은 선생님이 쓴 그대로 살린다 */}
                <p className="mt-1 whitespace-pre-wrap text-[14px] leading-relaxed text-brand-900">
                  {note.homeworkNote}
                </p>
              </div>
            ))}
          </TintBlock>
        </section>
      )}

      {todo.length > 0 && (
        <section className="mb-5">
          <SectionHead tone="accent" title="다시 제출 필요" count={todo.length} />
          <TintBlock tone="accent">
            {todo.map((item) => (
              <HomeworkRow key={item.homeworkId} item={item} tone="accent" onShowDescription={setDetail} />
            ))}
          </TintBlock>
        </section>
      )}

      {shown.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">
            {todo.length > 0 || (notesQuery.data ?? []).length > 0
              ? "지난 숙제가 없습니다."
              : "받은 숙제가 없습니다."}
          </p>
        </TintBlock>
      ) : (
        shown.map((group) => (
          <section key={group.lessonDate ?? "none"} className="mb-4 last:mb-0">
            <SectionHead tone="neutral" title={group.label} />
            <TintBlock tone="neutral">
              {group.items.map((item) => (
                <HomeworkRow key={item.homeworkId} item={item} tone="neutral" onShowDescription={setDetail} />
              ))}
            </TintBlock>
          </section>
        ))
      )}

      {detail && (
        <div
          className="fixed inset-0 z-50 flex items-end bg-black/50 sm:items-center
                     sm:justify-center"
          onClick={() => setDetail(null)}
          role="presentation"
        >
          <div
            className="max-h-[80vh] w-full overflow-y-auto rounded-t-2xl bg-white p-5
                       sm:max-w-md sm:rounded-2xl"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label={detail.title}
          >
            <p className="text-[16px] font-extrabold tracking-[-0.02em] text-brand-900">
              {detail.title}
            </p>
            {/* 줄바꿈은 선생님이 쓴 그대로 살린다 */}
            <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">
              {detail.description}
            </p>
            <button
              type="button"
              onClick={() => setDetail(null)}
              className="mt-4 w-full rounded-xl bg-brand-900 py-2.5 text-sm font-bold text-white"
            >
              닫기
            </button>
          </div>
        </div>
      )}
    </>
  );
}

/**
 * 목록의 한 줄. 두 덩어리가 같은 줄 모양을 쓰고 <b>배경만 다르다</b> —
 * 줄 구조까지 달라지면 같은 것을 두 가지로 그린 꼴이 된다.
 *
 * <p>주황 덩어리는 날짜 그룹 밖에 있어서 줄 안에 수업일이 남는다. 회색 덩어리는
 * 그룹 머리가 이미 날짜라 반 이름만 있으면 된다.
 */
function HomeworkRow({
  item,
  tone,
  onShowDescription,
}: {
  item: StudentHomeworkListItem;
  tone: "accent" | "neutral";
  onShowDescription: (item: StudentHomeworkListItem) => void;
}) {
  const overdue =
    item.status === "NOT_SUBMITTED" &&
    item.remainingMinutes !== null &&
    item.remainingMinutes < 0;

  return (
    <Link
      to={`/student/homeworks/${item.homeworkId}`}
      className={`block px-3.5 py-3.5 transition-colors ${
        tone === "accent" ? "active:bg-accent-100/60" : "active:bg-slate-50"
      }`}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="min-w-0 flex-1 truncate text-[15px] font-bold tracking-[-0.015em]
                         text-brand-900">
          {item.title}
        </span>
        {item.kind === "GRID" ? (
          <Badge tone={gradeTone(item.result)}>
            {gradeLabel(item.result, item.completionRate, item.resolvedByResubmission)}
          </Badge>
        ) : item.status === "NOT_SUBMITTED" ? (
          <Badge tone={overdue ? "danger" : "warn"}>
            {item.remainingMinutes !== null
              ? remainingLabel(item.remainingMinutes)
              : SUBMISSION_LABELS[item.status]}
          </Badge>
        ) : (
          <Badge tone="ok">{SUBMISSION_LABELS[item.status]}</Badge>
        )}
      </div>

      <p
        className={`tnum mt-0.5 text-[11.5px] ${
          tone === "accent" ? "text-accent-700/75" : "text-slate-500"
        }`}
      >
        {item.classRoomName}
        {tone === "accent" && item.lessonDate !== null && ` · ${item.lessonDate} 수업`}
      </p>

      {/* ONLINE의 남은 시간은 위 배지가 이미 보여준다. GRID는 재제출을 연 열에만
          마감이 있고, 없으면 남은 시간도 없다 — 0을 보여주면 "마감 임박"으로 읽힌다 */}
      {/* 지난 숙제 덩어리에서는 회색이다. 다 낸 숙제 옆의 주황은 "아직 안 한 것"이라는
          뜻을 거짓으로 만든다 — 색이 상태를 말하려면 상태에 따라 변해야 한다 */}
      {item.kind === "GRID" && item.remainingMinutes !== null && (
        <p
          className={`tnum mt-1 text-[11.5px] font-semibold ${
            tone === "accent" ? "text-accent-600" : "text-slate-500"
          }`}
        >
          {remainingLabel(item.remainingMinutes)}
        </p>
      )}

      {/* 상세 내용이 있는 줄만. 링크 안에 버튼을 넣으면 눌러도 링크가 먼저 먹으므로
          stopPropagation과 preventDefault가 둘 다 필요하다 */}
      {item.description && (
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onShowDescription(item);
          }}
          className="mt-1.5 text-[11.5px] font-semibold text-brand-600 underline"
        >
          내용 보기
        </button>
      )}

      <div className="mt-1.5 flex flex-wrap gap-1">
        {/* GRID는 resubmitRequired가 열려야만 다시 낼 수 있다. ONLINE의 상태는
            위 배지가 이미 알려주므로 여기서는 GRID 재제출만 따로 짚는다 */}
        {item.kind === "GRID" && item.resubmitRequired && <Badge tone="warn">다시 제출 필요</Badge>}
        {item.isLate && <Badge tone="neutral">늦게 냄</Badge>}
        {item.photoCount > 0 && <Badge tone="neutral">사진 {item.photoCount}장</Badge>}
        {item.hasVideo && <Badge tone="neutral">영상</Badge>}
      </div>
    </Link>
  );
}
