import { CorrectCountChart } from "../../../shared/components/CorrectCountChart";
import { ScoreResultBadge, ScoreValueText } from "../../../shared/components/ScoreValue";
import type { StudentScoreItem, StudentScoreSection } from "../../../shared/score/types";

/** 그 주의 한 종류. section은 그래프에 쓸 지난 주차들을 그대로 들고 있다. */
export interface WeekTest {
  section: StudentScoreSection;
  item: StudentScoreItem;
  /** 선택한 주차까지만 자른 시계열. 그 뒤 주차는 아직 안 일어난 일이라 그리지 않는다. */
  history: StudentScoreItem[];
}

/**
 * 그 주 테스트 결과 + 그때까지의 흐름.
 *
 * <p><b>기록이 없는 종류는 줄을 만들지 않는다.</b> "기입 안 하면 성적 확인에 아예
 * 안 보이게"가 규칙이라 4종을 빈칸으로 늘어놓으면 안 본 시험이 0점처럼 읽힌다.
 *
 * <p>막대는 본인 정답률 하나다. 반 평균·등수·백분위 막대를 옆에 세우지 마라 —
 * 계산도 노출도 하지 않기로 확정된 값이다.
 */
export function ReportTestCard({ tests }: { tests: WeekTest[] }) {
  return (
    <div className="space-y-2">
      <div className="card divide-y divide-slate-100">
        {tests.map(({ section, item }) => (
          <TestRow key={section.testType} label={section.label} item={item} />
        ))}
      </div>

      {/*
        그래프 종류는 서버가 정한다(chartKind). 단어·리뷰는 NONE이다 —
        단어는 "12/15" 텍스트, 리뷰는 통과 배지뿐이다.
      */}
      {tests
        .filter(({ section, history }) => section.chartKind !== "NONE" && history.length >= 2)
        .map(({ section, item, history }) => (
          <section key={`chart-${section.testType}`} className="card p-4">
            <div className="flex items-baseline justify-between gap-2">
              <h4 className="section-title">{section.label} 추이</h4>
              <p className="text-[11px] text-slate-400">{item.weekLabel}까지</p>
            </div>
            <div className="mt-2">
              <CorrectCountChart
                items={history}
                chartKind={section.chartKind}
                highlight={item}
              />
            </div>
          </section>
        ))}
    </div>
  );
}

function TestRow({ label, item }: { label: string; item: StudentScoreItem }) {
  return (
    <div className="p-4">
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-semibold text-brand-900">{label}</span>
        <span className="shrink-0 text-right text-sm">
          <ScoreValueText item={item} />
          <ScoreResultBadge item={item} />
        </span>
      </div>
    </div>
  );
}
