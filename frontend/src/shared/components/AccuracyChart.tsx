import type { StudentScoreItem } from "../score/types";

/**
 * 정답률 꺾은선. 인라인 SVG다 — 차트 라이브러리를 넣을 만한 화면이 이것뿐이다.
 *
 * <p><b>items 순서를 그대로 그린다.</b> 서버가 year·month·week 오름차순으로 내려준다.
 * 다시 정렬하면 달이 바뀌는 지점에서 어긋난다.
 *
 * <p>시험을 안 본 주는 배열에 없어 선이 0으로 떨어지지 않는다. 빈 점을 채워 넣지 마라.
 * 반 평균선·등수 같은 상대 지표는 그리지 않는다.
 */
export function AccuracyChart({ items }: { items: StudentScoreItem[] }) {
  // accuracy가 null인 항목(전체 문항 수 미입력)은 점을 찍을 수 없다
  const points = items.filter(
    (item): item is StudentScoreItem & { accuracy: number } => item.accuracy !== null,
  );
  if (points.length === 0) return null;

  // 100% 고정 축이다. 데이터 최대값으로 축을 잡으면 주마다 기울기가 달라 보인다
  const width = 320;
  const height = 150;
  const padding = { top: 12, right: 12, bottom: 28, left: 32 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const x = (index: number) =>
    padding.left
    + (points.length === 1 ? plotWidth / 2 : (plotWidth * index) / (points.length - 1));
  const y = (accuracy: number) => padding.top + plotHeight * (1 - accuracy / 100);

  const line = points.map((item, i) => `${x(i)},${y(item.accuracy)}`).join(" ");
  const average = points.reduce((sum, item) => sum + item.accuracy, 0) / points.length;
  const key = (item: StudentScoreItem) => `${item.year}-${item.month}-${item.week}`;

  return (
    <div className="space-y-2">
      {/* 본인 평균이다. 반 평균이 아니다 */}
      <p className="text-right text-xs text-slate-500">
        최근 {points.length}회 평균 {average.toFixed(1)}%
      </p>

      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-36 w-full min-w-[280px]"
          role="img"
          aria-label="주차별 정답률 추이"
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
          {points.map((item, i) => (
            <circle key={key(item)} cx={x(i)} cy={y(item.accuracy)} r={3.5} fill="#0f172a" />
          ))}

          {/* 라벨이 겹치면 양 끝과 중간만 보여준다 */}
          {points.map((item, i) =>
            points.length <= 6 || i === 0 || i === points.length - 1
              || i === Math.floor(points.length / 2) ? (
              <text
                key={`label-${key(item)}`}
                x={x(i)}
                y={height - 8}
                fontSize={9}
                fill="#64748b"
                textAnchor="middle"
              >
                {item.weekLabel}
              </text>
            ) : null,
          )}
        </svg>
      </div>
    </div>
  );
}
