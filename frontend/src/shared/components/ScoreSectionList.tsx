import type { StudentScoreData, StudentScoreSection } from "../score/types";
import { CorrectCountChart } from "./CorrectCountChart";
import { SectionHead, TintBlock } from "./Section";
import { ScoreResultBadge, ScoreValueText } from "./ScoreValue";

/** 이 줄 수를 넘으면 구획 안에서 스크롤한다. 주차가 쌓여도 화면 길이가 늘지 않는다. */
const VISIBLE_ROWS = 5;

/**
 * S-7 · P-4 공용. <b>학생과 학부모가 같은 화면을 본다.</b>
 *
 * <p>서버가 데이터 있는 종류만 내려주므로 여기서 걸러내지 않는다 — 그 반이 안 보는
 * 시험은 섹션 자체가 없다.
 *
 * <p>배지 판정도 서버가 준 retestScheduled·retestPassed를 그대로 쓴다.
 * 여기서 result로 다시 계산하면 화면마다 규칙이 갈린다.
 *
 * <p>정기고사는 이 응답에 없다. 등수·백분위·반 평균도 없다.
 */
export function ScoreSectionList({ data }: { data: StudentScoreData }) {
  return (
    <div className="space-y-5">
      {/*
        빨강이 아니라 주황이다. 빨강은 이 앱에서 "결석·위험" 하나만 뜻하기로 해 뒀고,
        재시험 예정은 위험이 아니라 <b>학생이 아직 처리 안 한 것</b>이다 —
        미완료 숙제와 같은 뜻이라 같은 색을 쓴다. tailwind.config의 accent 주석을 봐라.
      */}
      {data.retestScheduled.length > 0 && (
        <section>
          <SectionHead tone="accent" title="재시험 예정" count={data.retestScheduled.length} />
          <TintBlock tone="accent">
            {data.retestScheduled.map((notice) => (
              <p
                key={`${notice.testType}-${notice.weekLabel}`}
                className="px-3.5 py-2.5 text-[13.5px] font-semibold text-accent-700"
              >
                {notice.label}
              </p>
            ))}
          </TintBlock>
        </section>
      )}

      {data.sections.length === 0 && (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">
            아직 기록된 성적이 없습니다.
          </p>
        </TintBlock>
      )}

      {data.sections.map((section) => (
        <Section key={section.testType} section={section} />
      ))}
    </div>
  );
}

function Section({ section }: { section: StudentScoreSection }) {
  // 목록은 최신이 위다. 응답은 그래프용 오름차순이라 여기서만 뒤집는다
  const rows = [...section.items].reverse();
  const scrolls = rows.length > VISIBLE_ROWS;

  return (
    <section>
      <SectionHead tone="brand" title={section.label} />
      <TintBlock tone="neutral" className="space-y-3 p-4 [&>*+*]:border-0">
        {section.chartKind !== "NONE" && (
          <CorrectCountChart items={section.items} chartKind={section.chartKind} />
        )}
      {/*
        주차가 쌓이면 종류마다 수십 줄이 되어 화면이 끝없이 길어진다. 그래서 구획
        안에서만 스크롤한다. 높이는 5줄보다 조금 크게 잡아 다음 줄이 반쯤 걸치게
        둔다 — 그게 "아래에 더 있다"를 알리는 유일한 신호다. 딱 맞게 자르면
        스크롤바가 없는 모바일에서 목록이 여기서 끝난 것처럼 보인다.
        overscroll-contain이 없으면 끝까지 내렸을 때 페이지가 이어서 밀린다.
      */}
      <ul
        className={`divide-y divide-slate-100 text-sm ${
          scrolls ? "max-h-48 overflow-y-auto overscroll-contain" : ""
        }`}
      >
        {rows.map((item) => (
          <li
            key={`${item.year}-${item.month}-${item.week}`}
            className="flex items-start justify-between gap-3 py-2"
          >
            <span className="shrink-0 font-medium text-brand-900">{item.weekLabel}</span>
            <span className="text-right">
              <ScoreValueText item={item} />
              <ScoreResultBadge item={item} />
            </span>
          </li>
        ))}
      </ul>
      </TintBlock>
    </section>
  );
}
