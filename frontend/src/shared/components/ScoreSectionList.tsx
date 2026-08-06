import type { StudentScoreData, StudentScoreItem, StudentScoreSection } from "../score/types";
import { AccuracyChart } from "./AccuracyChart";

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
    <div className="space-y-3">
      {data.retestScheduled.length > 0 && (
        <section className="rounded-xl border border-red-200 bg-red-50 p-4">
          <p className="text-sm font-semibold text-red-800">
            재시험 예정 {data.retestScheduled.length}건
          </p>
          <ul className="mt-1 space-y-0.5 text-sm text-red-700">
            {data.retestScheduled.map((notice) => (
              <li key={`${notice.testType}-${notice.weekLabel}`}>{notice.label}</li>
            ))}
          </ul>
        </section>
      )}

      {data.sections.length === 0 && (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          아직 기록된 성적이 없습니다.
        </p>
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
    <section className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">{section.label}</h3>
      {section.chart && <AccuracyChart items={section.items} />}
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
            <span className="shrink-0 text-slate-900">{item.weekLabel}</span>
            <span className="text-right">
              <ValueText item={item} />
              <ResultBadge item={item} />
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ValueText({ item }: { item: StudentScoreItem }) {
  if (item.internalCorrect !== null || item.externalCorrect !== null) {
    return (
      <span className="text-slate-700">
        내부 {item.internalCorrect ?? "—"}/{item.internalTotal ?? "—"} · 외부{" "}
        {item.externalCorrect ?? "—"}/{item.externalTotal ?? "—"}
      </span>
    );
  }
  if (item.correctCount !== null) {
    return (
      <span className="text-slate-700">
        {item.correctCount}/{item.totalCount}
        {item.accuracy !== null && (
          <span className="ml-1 text-xs text-slate-500">{item.accuracy}%</span>
        )}
      </span>
    );
  }
  return null;
}

/**
 * 통과 / 재시험 예정 / 재시험 통과.
 * 판정은 서버 값을 그대로 쓴다. "재시험 미통과" 상태는 없다 —
 * 재시험을 또 떨어지면 선생님이 체크를 안 하므로 "재시험 예정"이 유지된다.
 *
 * <p>순서가 중요하다. retestScheduled를 먼저 보는 이유는 둘 다 result가 FAIL이기
 * 때문이다 — 재시험 통과 체크가 되면 retestScheduled가 false가 되어 아래로 내려간다.
 */
function ResultBadge({ item }: { item: StudentScoreItem }) {
  if (item.result === null) return null;
  if (item.retestScheduled) {
    return (
      <span className="ml-2 whitespace-nowrap rounded-full bg-red-100 px-2 py-0.5 text-xs
                       text-red-700">
        재시험 예정
      </span>
    );
  }
  if (item.retestPassed) {
    return (
      <span className="ml-2 whitespace-nowrap rounded-full bg-blue-100 px-2 py-0.5 text-xs
                       text-blue-700">
        재시험 통과
      </span>
    );
  }
  return (
    <span className="ml-2 whitespace-nowrap rounded-full bg-emerald-100 px-2 py-0.5 text-xs
                     text-emerald-700">
      통과
    </span>
  );
}
