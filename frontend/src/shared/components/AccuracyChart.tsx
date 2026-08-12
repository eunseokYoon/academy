import type { StudentScoreItem } from "../score/types";
import { weekKey } from "../score/week";

/** 이번 주 점을 찍을 위치. 안 넘기면 표시하지 않는다(성적 목록 화면). */
export interface ChartHighlight {
  year: number;
  month: number;
  week: number;
}

/**
 * 정답률 꺾은선. 인라인 SVG다 — 차트 라이브러리를 넣을 만한 화면이 이것뿐이다.
 *
 * <p><b>items 순서를 그대로 그린다.</b> 서버가 year·month·week 오름차순으로 내려준다.
 * 다시 정렬하면 달이 바뀌는 지점에서 어긋난다.
 *
 * <p>시험을 안 본 주는 배열에 없어 선이 0으로 떨어지지 않는다. 빈 점을 채워 넣지 마라.
 * 반 평균선·등수 같은 상대 지표는 그리지 않는다.
 *
 * <p>highlight를 넘기면 그 주 점만 주황으로 크게 찍는다. 주간 레포트(P-6)에서
 * "이번 주가 흐름의 어디쯤인가"를 보여주는 용도다 — 주황이 이 앱에서 뜻하는
 * "지금 여기"가 그대로 적용된 것이다. 두 점을 주황으로 만들지 마라.
 */
export function AccuracyChart({
  items,
  highlight,
}: {
  items: StudentScoreItem[];
  highlight?: ChartHighlight;
}) {
  // accuracy가 null인 항목(전체 문항 수 미입력)은 점을 찍을 수 없다
  const points = items.filter(
    (item): item is StudentScoreItem & { accuracy: number } => item.accuracy !== null,
  );
  if (points.length === 0) return null;

  // 100% 고정 축이다. 데이터 최대값으로 축을 잡으면 주마다 기울기가 달라 보인다
  const width = 320;
  const height = 150;
  const padding = { top: 14, right: 14, bottom: 28, left: 32 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const x = (index: number) =>
    padding.left
    + (points.length === 1 ? plotWidth / 2 : (plotWidth * index) / (points.length - 1));
  const y = (accuracy: number) => padding.top + plotHeight * (1 - accuracy / 100);

  const line = points.map((item, i) => `${x(i)},${y(item.accuracy)}`).join(" ");
  const average = points.reduce((sum, item) => sum + item.accuracy, 0) / points.length;
  const key = (item: StudentScoreItem) => `${item.year}-${item.month}-${item.week}`;

  const marked = highlight ? weekKey(highlight.year, highlight.month, highlight.week) : null;
  const isMarked = (item: StudentScoreItem) =>
    marked !== null && weekKey(item.year, item.month, item.week) === marked;

  return (
    <div className="space-y-2">
      {/* 본인 평균이다. 반 평균이 아니다 */}
      <p className="tnum text-right text-xs text-slate-500">
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

          {/* 선은 로고의 남색이다. 앱바·본문 잉크와 같은 값이라 화면에서 따로 놀지 않는다 */}
          <polyline points={line} fill="none" stroke="#1B2A44" strokeWidth={2} />
          {points.map((item, i) =>
            isMarked(item) ? (
              // 흰 테를 두르는 이유는 선 위에 겹쳐 앉기 때문이다. 없으면 점이 선에 묻힌다
              <circle
                key={key(item)}
                cx={x(i)}
                cy={y(item.accuracy)}
                r={5.5}
                fill="#D9542B"
                stroke="#fff"
                strokeWidth={2}
              />
            ) : (
              <circle key={key(item)} cx={x(i)} cy={y(item.accuracy)} r={3.5} fill="#1B2A44" />
            ),
          )}

          {/*
            라벨이 겹치면 양 끝과 중간만 보여준다.
            표시한 주는 그 규칙과 무관하게 항상 적는다 — 주황 점이 어느 주인지
            모르면 점을 찍은 의미가 없다.
          */}
          {points.map((item, i) =>
            isMarked(item) || points.length <= 6 || i === 0 || i === points.length - 1
              || i === Math.floor(points.length / 2) ? (
              <text
                key={`label-${key(item)}`}
                x={x(i)}
                y={height - 8}
                fontSize={9}
                fill={isMarked(item) ? "#D9542B" : "#64748b"}
                fontWeight={isMarked(item) ? 700 : 400}
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
