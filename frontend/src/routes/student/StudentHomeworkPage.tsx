import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { SUBMISSION_LABELS, remainingLabel } from "../../shared/homework/types";
import { listMyHomeworks } from "./api";
import type { StudentHomeworkListItem } from "./api";

/**
 * S-2 숙제 목록. 미제출이면서 마감이 가까운 것이 위로 온다(서버 정렬).
 *
 * <p>남은 시간은 서버가 계산해 내려준 값이다. 기기 시계가 틀려도 같은 값이 보인다.
 *
 * <p><b>두 덩어리로 나눈다.</b> 예전에는 흰 카드 여섯 장이 세로로 쌓여서, "지금 내야 하는
 * 것"과 "이미 끝난 것"이 같은 무게로 보였다 — 학생이 목록을 처음부터 끝까지 읽어야
 * 할 일을 찾을 수 있었다는 뜻이다. 위 덩어리만 주황이다.
 */
export default function StudentHomeworkPage() {
  const homeworks = useQuery({
    queryKey: ["student", "homeworks"],
    queryFn: () => listMyHomeworks({}),
  });

  if (homeworks.isPending) {
    return (
      <>
        <PageTitle>숙제</PageTitle>
        <p className="text-sm text-slate-400">불러오는 중…</p>
      </>
    );
  }

  const items = homeworks.data?.items ?? [];

  /*
    "지금 낼 수 있고, 내야 하는 것"의 정의다. 축이 둘이라 한 줄로 못 쓴다 —
    GRID는 선생님이 재제출을 열어 줘야만 낼 수 있고(status는 ⭕를 받아도
    NOT_SUBMITTED로 남는다), ONLINE은 아직 안 낸 것이 그대로 할 일이다.
    CLAUDE.md의 4-2·4-3이 근거다. 여기서 status로 GRID를 판정하지 마라.
  */
  const isTodo = (item: StudentHomeworkListItem) =>
    item.kind === "GRID" ? item.resubmitRequired : item.status === "NOT_SUBMITTED";

  const todo = items.filter(isTodo);
  const done = items.filter((item) => !isTodo(item));

  if (items.length === 0) {
    return (
      <>
        <PageTitle>숙제</PageTitle>
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">받은 숙제가 없습니다.</p>
        </TintBlock>
      </>
    );
  }

  return (
    <>
      <PageTitle>숙제</PageTitle>

      {todo.length > 0 && (
        <section className="mb-5">
          <SectionHead tone="accent" title="지금 낼 것" count={todo.length} />
          <TintBlock tone="accent">
            {todo.map((item) => (
              <HomeworkRow key={item.homeworkId} item={item} tone="accent" />
            ))}
          </TintBlock>
        </section>
      )}

      {done.length > 0 && (
        <section>
          <SectionHead tone="neutral" title={todo.length > 0 ? "지난 숙제" : "숙제"} />
          <TintBlock tone="neutral">
            {done.map((item) => (
              <HomeworkRow key={item.homeworkId} item={item} tone="neutral" />
            ))}
          </TintBlock>
        </section>
      )}
    </>
  );
}

/**
 * 목록의 한 줄. 두 덩어리가 같은 줄 모양을 쓰고 <b>배경만 다르다</b> —
 * 줄 구조까지 달라지면 같은 것을 두 가지로 그린 꼴이 된다.
 */
function HomeworkRow({
  item,
  tone,
}: {
  item: StudentHomeworkListItem;
  tone: "accent" | "neutral";
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
        {item.lessonDate !== null && ` · ${item.lessonDate} 수업`}
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
