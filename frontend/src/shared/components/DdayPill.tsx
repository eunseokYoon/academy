/**
 * 시험까지 남은 날. 참고 디자인에서 카드 오른쪽 위에 걸려 있던 알약이다.
 *
 * <p>화면에서 가장 진한 덩어리라 <b>한 화면에 하나만</b> 둔다.
 * 여러 개를 늘어놓으면 무엇이 급한지가 사라진다.
 *
 * <p>dDay가 0이면 "D-0"이 아니라 "D-DAY"다. D-0은 지나간 날처럼 읽힌다.
 */
export function DdayPill({ label, dDay }: { label: string; dDay: number }) {
  return (
    <div
      className="shrink-0 rounded-xl bg-gradient-to-b from-brand-700 to-brand-900 px-3 py-2
                 text-center text-white shadow-pill"
    >
      <p className="text-[10px] font-medium tracking-[0.08em] text-brand-200">{label}</p>
      <p className="tnum text-lg font-extrabold leading-tight tracking-[-0.02em]">
        {dDay === 0 ? "D-DAY" : `D-${dDay}`}
      </p>
    </div>
  );
}
