import type { StudentScoreItem } from "../score/types";

/**
 * 성적 한 칸의 값과 배지. 성적 목록(S-7 · P-4)과 주간 레포트(P-6)가 같이 쓴다.
 *
 * <p>두 화면이 각자 형식을 만들면 같은 성적이 한 곳에서는 "23/25",
 * 다른 곳에서는 "92점"으로 보인다. 여기가 정본이다.
 *
 * <p>정답률·환산 점수는 2026-09-10에 없앴다. 되살리지 마라 —
 * 서버가 accuracy에 null을 내리므로 화면에서 계산해야 하는데, 그러면 규칙이
 * 화면 쪽으로 옮겨 앉는다.
 */

/**
 * 클리닉은 내부·외부 두 값이라 correctCount가 비어 있고, 리뷰는 P/F뿐이라 둘 다 비어 있다.
 * 그래서 순서가 중요하다 — 내부·외부를 먼저 본다.
 */
export function ScoreValueText({ item }: { item: StudentScoreItem }) {
  if (item.internalCorrect !== null || item.externalCorrect !== null) {
    return (
      <span className="tnum text-slate-700">
        내부 {item.internalCorrect ?? "—"}/{item.internalTotal ?? "—"} · 외부{" "}
        {item.externalCorrect ?? "—"}/{item.externalTotal ?? "—"}
      </span>
    );
  }
  if (item.correctCount !== null) {
    return (
      <span className="tnum text-slate-700">
        {item.correctCount}/{item.totalCount}
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
export function ScoreResultBadge({ item }: { item: StudentScoreItem }) {
  if (item.result === null) return null;
  if (item.retestScheduled) {
    return <Pill className="bg-red-100 text-red-700">재시험 예정</Pill>;
  }
  if (item.retestPassed) {
    return <Pill className="bg-blue-100 text-blue-700">재시험 통과</Pill>;
  }
  return <Pill className="bg-emerald-100 text-emerald-700">통과</Pill>;
}

function Pill({ className, children }: { className: string; children: string }) {
  return (
    <span
      className={`ml-2 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${className}`}
    >
      {children}
    </span>
  );
}
