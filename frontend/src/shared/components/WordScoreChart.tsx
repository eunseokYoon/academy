import type { WordPoint } from "../score/types";

/**
 * 주차별 단어 테스트 꺾은선. 인라인 SVG다 — 차트 라이브러리를 넣을 만한 화면이 이것뿐이다.
 *
 * <p><b>서버가 준 points 순서를 그대로 그린다.</b> 다시 정렬하면 달이 바뀌는 지점에서 어긋난다.
 * 시험을 안 본 주는 배열에 없으므로 선이 0으로 떨어지지 않는다 — 빈 점을 채워 넣지 마라.
 *
 * <p>반 평균선·등수 같은 상대 지표는 그리지 않는다.
 */
export function WordScoreChart({ points }: { points: WordPoint[] }) {
  if (points.length === 0) {
    return (
      <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
        아직 단어 테스트 기록이 없습니다.
      </p>
    );
  }

  // 100점 만점 고정 축이다. 데이터 최대값으로 축을 잡으면 주마다 기울기가 달라 보인다
  const width = 320;
  const height = 160;
  const padding = { top: 12, right: 12, bottom: 28, left: 32 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const x = (index: number) =>
    padding.left + (points.length === 1 ? plotWidth / 2 : (plotWidth * index) / (points.length - 1));
  const y = (score: number) => padding.top + plotHeight * (1 - score / 100);

  const line = points.map((point, i) => `${x(i)},${y(point.score)}`).join(" ");
  const average =
    points.reduce((sum, point) => sum + point.score, 0) / points.length;

  return (
    <div className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
      <div className="flex items-baseline justify-between">
        <h3 className="text-sm font-semibold text-slate-900">단어 테스트</h3>
        {/* 본인 평균이다. 반 평균이 아니다 */}
        <span className="text-xs text-slate-500">
          최근 {points.length}회 평균 {average.toFixed(1)}점
        </span>
      </div>

      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-40 w-full min-w-[280px]"
          role="img"
          aria-label="주차별 단어 테스트 점수 추이"
        >
          {[0, 50, 100].map((tick) => (
            <g key={tick}>
              <line
                x1={padding.left}
                y1={y(tick)}
                x2={width - padding.right}
                y2={y(tick)}
                stroke="#e2e8f0"
                strokeWidth={1}
              />
              <text x={4} y={y(tick) + 4} fontSize={9} fill="#94a3b8">
                {tick}
              </text>
            </g>
          ))}

          <polyline points={line} fill="none" stroke="#0f172a" strokeWidth={2} />
          {points.map((point, i) => (
            <circle key={`${point.year}-${point.month}-${point.week}`}
              cx={x(i)} cy={y(point.score)} r={3.5} fill="#0f172a" />
          ))}

          {/* 라벨이 겹치면 양 끝과 중간만 보여준다 */}
          {points.map((point, i) =>
            points.length <= 6 || i === 0 || i === points.length - 1
              || i === Math.floor(points.length / 2) ? (
              <text
                key={`label-${point.year}-${point.month}-${point.week}`}
                x={x(i)}
                y={height - 8}
                fontSize={9}
                fill="#64748b"
                textAnchor="middle"
              >
                {point.label}
              </text>
            ) : null,
          )}
        </svg>
      </div>

      <ul className="divide-y divide-slate-100 text-sm">
        {[...points].reverse().map((point) => (
          <li
            key={`row-${point.year}-${point.month}-${point.week}`}
            className="flex items-center justify-between py-2"
          >
            <div>
              <p className="text-slate-900">{point.label}</p>
              <p className="text-xs text-slate-500">{point.examName}</p>
            </div>
            <span className="font-semibold text-slate-900">{point.score}점</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
