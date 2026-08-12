import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { remainingLabel } from "../../shared/homework/types";
import { TAKE_STATUS_LABELS } from "../../shared/onlinetest/types";
import { listMyOnlineTests } from "./api";
import type { StudentOnlineTestListItem } from "./api";

/** S-10 목록. 공개·재원·시작시각 조건은 서버가 이미 걸러서 내려준다. */
export default function StudentOnlineTestPage() {
  const { data, isPending } = useQuery({
    queryKey: ["student", "online-tests"],
    queryFn: listMyOnlineTests,
  });

  return (
    <div>
      <PageTitle>온라인 테스트</PageTitle>
      <p className="mb-4 rounded-xl bg-white px-3.5 py-2.5 text-[12px] leading-relaxed
                    text-slate-600 shadow-card">
        종이 시험지를 먼저 푼 뒤 답만 입력하세요. 작성 중인 답은 자동으로 저장됩니다.
      </p>

      {isPending || !data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : data.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">응시할 테스트가 없습니다.</p>
        </TintBlock>
      ) : (
        (() => {
          /*
            아직 안 낸 것만 주황이다. 제출한 테스트까지 주황이면 "지금 할 일"이라는
            뜻이 사라진다 — 숙제 목록(S-2)과 같은 규칙이다.
          */
          const todo = data.filter((test) => test.status !== "SUBMITTED");
          const done = data.filter((test) => test.status === "SUBMITTED");
          return (
            <>
              {todo.length > 0 && (
                <section className="mb-5">
                  <SectionHead tone="accent" title="응시할 테스트" count={todo.length} />
                  <TintBlock tone="accent">
                    {todo.map((test) => (
                      <TestRow key={test.testId} test={test} tone="accent" />
                    ))}
                  </TintBlock>
                </section>
              )}
              {done.length > 0 && (
                <section>
                  <SectionHead tone="neutral" title="제출 완료" />
                  <TintBlock tone="neutral">
                    {done.map((test) => (
                      <TestRow key={test.testId} test={test} tone="neutral" />
                    ))}
                  </TintBlock>
                </section>
              )}
            </>
          );
        })()
      )}
    </div>
  );
}

/** 목록의 한 줄. 두 덩어리가 같은 모양을 쓰고 배경만 다르다. */
function TestRow({
  test,
  tone,
}: {
  test: StudentOnlineTestListItem;
  tone: "accent" | "neutral";
}) {
  const closed = test.remainingMinutes != null && test.remainingMinutes < 0;
  const submitted = test.status === "SUBMITTED";

  return (
    <Link
      to={`/student/online-tests/${test.testId}`}
      className={`block px-3.5 py-3.5 transition-colors ${
        tone === "accent" ? "active:bg-accent-100/60" : "active:bg-slate-50"
      }`}
    >
      <p className="text-[15px] font-bold tracking-[-0.015em] text-brand-900">{test.title}</p>
      <p
        className={`tnum mt-0.5 text-[11.5px] ${
          tone === "accent" ? "text-accent-700/75" : "text-slate-500"
        }`}
      >
        {test.classRoomName} · {test.questionCount}문항
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <Badge tone={submitted ? "ok" : closed ? "danger" : "warn"}>
          {TAKE_STATUS_LABELS[test.status]}
        </Badge>
        {!submitted && test.status === "IN_PROGRESS" && (
          <Badge>
            {test.answeredCount} / {test.questionCount} 입력
          </Badge>
        )}
        {/* 서버가 계산한 값이다. 클라이언트 시계로 다시 계산하지 않는다 */}
        {!submitted && test.remainingMinutes != null && (
          <span
            className={`tnum text-[11.5px] font-semibold ${
              closed ? "text-red-600" : "text-slate-500"
            }`}
          >
            {remainingLabel(test.remainingMinutes)}
          </span>
        )}
      </div>
    </Link>
  );
}
