import type { ScoreChartKind, StudentScoreItem } from "../score/types";
import { weekKey } from "../score/week";

/** 이번 주 막대를 표시할 위치. 안 넘기면 표시하지 않는다(성적 목록 화면). */
export interface ChartHighlight {
  year: number;
  month: number;
  week: number;
}

/*
  내부지문은 버건디, 외부지문은 남색이다(2026-09-10 회의).

  버건디를 tailwind 테마에 올리지 마라. 팔레트에 있으면 버튼·배지로 번지고,
  그러면 "빨강은 결석·위험 하나만"이라는 규칙이 흐려진다. 여기 상수로 둔다.
  남색은 로고·앱바와 같은 brand-900 값이라 화면에서 따로 놀지 않는다.
*/
const INTERNAL = "#7A2E3A";
const EXTERNAL = "#1B2A44";
/** 표시한 주. 이 앱에서 주황은 "지금 여기" 하나만 뜻한다. */
const MARKED = "#D9542B";

/**
 * 맞힌 개수 막대. 인라인 SVG다 — 차트 라이브러리를 넣을 만한 화면이 이것뿐이다.
 *
 * <p><b>정답률이 아니라 개수다</b>(2026-09-10). 그래서 꺾은선이 아니다 —
 * 주마다 전체 문항 수가 달라서 개수를 선으로 이으면 기울기가 거짓말을 한다.
 *
 * <p><b>items 순서를 그대로 그린다.</b> 서버가 year·month·week 오름차순으로 내려준다.
 *
 * <p>Y축은 <b>섹션 전체에서 가장 큰 전체 문항 수 하나로 고정</b>한다. 주마다 다시
 * 잡으면 막대 높이를 주끼리 비교할 수 없다.
 *
 * <p>시험을 안 본 주는 배열에 없다. 빈 막대를 채워 넣지 마라.
 * 반 평균선·등수 같은 상대 지표는 그리지 않는다.
 */
export function CorrectCountChart({
  items,
  chartKind,
  highlight,
}: {
  items: StudentScoreItem[];
  chartKind: ScoreChartKind;
  highlight?: ChartHighlight;
}) {
  if (chartKind === "NONE") return null;

  const split = chartKind === "SPLIT_BAR";

  /* 전체 문항 수가 없는 칸은 막대를 그릴 수 없다. 0으로 채우면 "다 틀렸다"로 읽힌다 */
  const points = items.filter((item) =>
    split
      ? item.internalTotal !== null || item.externalTotal !== null
      : item.correctCount !== null && item.totalCount !== null,
  );
  if (points.length === 0) return null;

  const yMax = Math.max(
    1,
    ...points.map((item) =>
      split
        ? Math.max(item.internalTotal ?? 0, item.externalTotal ?? 0)
        : (item.totalCount ?? 0),
    ),
  );

  const width = 320;
  const height = 150;
  const padding = { top: 14, right: 14, bottom: 28, left: 32 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const slot = plotWidth / points.length;
  /* 막대 폭은 칸의 절반까지다. 주가 둘뿐일 때 막대가 판처럼 넓어지는 걸 막는다 */
  const barWidth = Math.min(slot * 0.5, split ? 14 : 22);
  const y = (value: number) => padding.top + plotHeight * (1 - value / yMax);
  const centerX = (index: number) => padding.left + slot * (index + 0.5);

  const marked = highlight ? weekKey(highlight.year, highlight.month, highlight.week) : null;
  const isMarked = (item: StudentScoreItem) =>
    marked !== null && weekKey(item.year, item.month, item.week) === marked;
  const key = (item: StudentScoreItem) => `${item.year}-${item.month}-${item.week}`;

  /* 0~yMax를 반으로 나눈 세 줄. yMax가 홀수면 가운데가 소수라 반올림해 적는다 */
  const ticks = [0, Math.round(yMax / 2), yMax];

  return (
    <div className="space-y-2">
      <p className="tnum text-right text-xs text-slate-500">
        {split ? "내부 · 외부 맞힌 개수" : "맞힌 개수"} · 전체 {yMax}문항
      </p>

      <div className="overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="h-36 w-full min-w-[280px]"
          role="img"
          aria-label={split ? "주차별 내부·외부 맞힌 개수" : "주차별 맞힌 개수"}
        >
          {ticks.map((tick) => (
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

          {points.map((item, i) => {
            const cx = centerX(i);
            if (split) {
              /* 두 막대를 중심 기준 좌우로 벌린다. 사이 간격 2px */
              const left = cx - barWidth - 1;
              const right = cx + 1;
              return (
                <g key={key(item)}>
                  {item.internalCorrect !== null && (
                    <rect
                      x={left}
                      y={y(item.internalCorrect)}
                      width={barWidth}
                      height={Math.max(1, y(0) - y(item.internalCorrect))}
                      rx={2}
                      fill={INTERNAL}
                    />
                  )}
                  {item.externalCorrect !== null && (
                    <rect
                      x={right}
                      y={y(item.externalCorrect)}
                      width={barWidth}
                      height={Math.max(1, y(0) - y(item.externalCorrect))}
                      rx={2}
                      fill={EXTERNAL}
                    />
                  )}
                </g>
              );
            }
            const value = item.correctCount ?? 0;
            return (
              <rect
                key={key(item)}
                x={cx - barWidth / 2}
                y={y(value)}
                width={barWidth}
                height={Math.max(1, y(0) - y(value))}
                rx={2}
                fill={isMarked(item) ? MARKED : EXTERNAL}
              />
            );
          })}

          {/*
            라벨이 겹치면 양 끝과 가운데만 보여준다.
            표시한 주는 그 규칙과 무관하게 항상 적는다 — 어느 주인지 모르면 표시한 뜻이 없다.
          */}
          {points.map((item, i) =>
            isMarked(item) || points.length <= 6 || i === 0 || i === points.length - 1
              || i === Math.floor(points.length / 2) ? (
              <text
                key={`label-${key(item)}`}
                x={centerX(i)}
                y={height - 8}
                fontSize={9}
                fill={isMarked(item) ? MARKED : "#64748b"}
                fontWeight={isMarked(item) ? 700 : 400}
                textAnchor="middle"
              >
                {item.weekLabel}
              </text>
            ) : null,
          )}
        </svg>
      </div>

      {/* 2계열일 때만 범례가 필요하다. 색 둘을 글씨 없이 두면 무엇인지 모른다 */}
      {split && (
        <ul className="flex gap-3 text-[11px] text-slate-500">
          <li className="flex items-center gap-1.5">
            <span
              className="inline-block h-2.5 w-2.5 rounded-sm"
              style={{ backgroundColor: INTERNAL }}
            />
            내부지문
          </li>
          <li className="flex items-center gap-1.5">
            <span
              className="inline-block h-2.5 w-2.5 rounded-sm"
              style={{ backgroundColor: EXTERNAL }}
            />
            외부지문
          </li>
        </ul>
      )}
    </div>
  );
}
