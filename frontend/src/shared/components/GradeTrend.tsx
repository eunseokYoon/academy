import type { ScoreItem } from "../score/types";

/**
 * 내신·모의 등급 추이. <b>Y축이 역방향이다</b> — 등급은 낮을수록 좋아서
 * 1등급이 위에 와야 "올라갔다"로 읽힌다.
 *
 * <p>서버는 examDate 내림차순으로 주므로 그래프는 뒤집어서 시간순으로 그린다.
 * 등급이 없는 성적(원점수만 있는 모의고사)은 점을 찍지 않는다.
 */
export function GradeTrend({ items }: { items: ScoreItem[] }) {
  const graded = [...items].reverse().filter((item) => item.gradeLevel != null);
  if (graded.length < 2) return null;

  const width = 320;
  const height = 120;
  const padding = { top: 12, right: 12, bottom: 24, left: 28 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const x = (index: number) => padding.left + (plotWidth * index) / (graded.length - 1);
  // 1등급이 위, 9등급이 아래
  const y = (grade: number) => padding.top + (plotHeight * (grade - 1)) / 8;

  const line = graded.map((item, i) => `${x(i)},${y(item.gradeLevel as number)}`).join(" ");

  return (
    <div className="overflow-x-auto">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="h-32 w-full min-w-[280px]"
        role="img"
        aria-label="등급 추이 (위쪽이 1등급)"
      >
        {[1, 5, 9].map((grade) => (
          <g key={grade}>
            <line
              x1={padding.left}
              y1={y(grade)}
              x2={width - padding.right}
              y2={y(grade)}
              stroke="#e2e8f0"
              strokeWidth={1}
            />
            <text x={4} y={y(grade) + 4} fontSize={9} fill="#94a3b8">
              {grade}등
            </text>
          </g>
        ))}

        <polyline points={line} fill="none" stroke="#0f172a" strokeWidth={2} />
        {graded.map((item, i) => (
          <circle
            key={item.scoreId}
            cx={x(i)}
            cy={y(item.gradeLevel as number)}
            r={3.5}
            fill="#0f172a"
          />
        ))}
        {graded.map((item, i) =>
          i === 0 || i === graded.length - 1 ? (
            <text
              key={`label-${item.scoreId}`}
              x={x(i)}
              y={height - 6}
              fontSize={9}
              fill="#64748b"
              textAnchor="middle"
            >
              {item.examDate.slice(2).replace(/-/g, ".")}
            </text>
          ) : null,
        )}
      </svg>
    </div>
  );
}
