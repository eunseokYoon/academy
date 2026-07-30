import type { ScoreItem } from "../score/types";
import { GradeTrend } from "./GradeTrend";

/**
 * 내신·모의 섹션. 등급을 크게, 원점수는 보조로 표시한다.
 * 등수·백분위·반 평균은 서버가 내려주지 않고 여기서도 계산하지 않는다.
 */
export function ScoreSections({
  internal,
  mock,
}: {
  internal: ScoreItem[];
  mock: ScoreItem[];
}) {
  return (
    <div className="space-y-3">
      <ScoreSection title="내신" items={internal} />
      <ScoreSection title="모의고사" items={mock} />
    </div>
  );
}

function ScoreSection({ title, items }: { title: string; items: ScoreItem[] }) {
  return (
    <section className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>

      {items.length === 0 ? (
        <p className="py-4 text-center text-sm text-slate-500">기록이 없습니다.</p>
      ) : (
        <>
          <GradeTrend items={items} />
          <ul className="divide-y divide-slate-100 text-sm">
            {items.map((item) => (
              <li key={item.scoreId} className="flex items-start justify-between gap-3 py-2">
                <div className="min-w-0">
                  <p className="truncate text-slate-900">{item.examName}</p>
                  <p className="text-xs text-slate-500">
                    {item.subject} · {item.examDate.replace(/-/g, ".")}
                  </p>
                  {item.memo && (
                    <p className="mt-0.5 whitespace-pre-wrap text-xs text-slate-500">
                      {item.memo}
                    </p>
                  )}
                </div>
                <div className="shrink-0 text-right">
                  {item.gradeLevel != null && (
                    <p className="text-base font-semibold text-slate-900">
                      {item.gradeLevel}등급
                    </p>
                  )}
                  {item.rawScore != null && (
                    <p className="text-xs text-slate-500">{item.rawScore}점</p>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
