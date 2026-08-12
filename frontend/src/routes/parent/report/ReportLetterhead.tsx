/**
 * 주간 레포트의 표지. 이 화면에서 과감한 건 여기 하나다 — 나머지는 전부 흰 카드다.
 *
 * <p>종이 성적표를 받았을 때 맨 위에 있는 것: 누구의, 어느 반, 몇 주차 레포트인가.
 * 학부모가 이 화면을 열고 0.5초 안에 확인해야 하는 값이고, 여러 자녀를 둔
 * 학부모에게는 <b>지금 누구를 보고 있는지</b>가 그 자체로 정보다.
 *
 * <p>주황은 주차와 아래 헤어라인 두 곳뿐이다. 이 앱에서 주황이 뜻하는 "지금 여기"가
 * 레포트에서는 "지금 보고 있는 주"다. 요약 숫자까지 주황으로 칠하지 마라 —
 * 넷이 다 같은 색이면 어느 것도 눈에 안 띈다.
 */
export function ReportLetterhead({
  name,
  classRooms,
  month,
  week,
  stats,
}: {
  name: string;
  classRooms: string[];
  month: number;
  week: number;
  stats: { label: string; value: string }[];
}) {
  return (
    <section className="overflow-hidden rounded-2xl bg-brand-900 shadow-card">
      <div className="px-5 pb-4 pt-5">
        <p className="text-[11px] font-semibold tracking-[0.14em] text-brand-300">주간 레포트</p>

        <div className="mt-2 flex items-end justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate text-[26px] font-bold leading-none tracking-[-0.02em] text-white">
              {name}
            </h2>
            {/* 반이 둘 이상인 학생이 있다. 가운뎃점으로 이어 한 줄에 둔다 */}
            {classRooms.length > 0 && (
              <p className="mt-2 truncate text-xs text-brand-200">{classRooms.join(" · ")}</p>
            )}
          </div>
          <p className="tnum shrink-0 text-right text-xl font-bold leading-none text-white">
            {month}월 <span className="text-accent-400">{week}주</span>
          </p>
        </div>
      </div>

      <div className="h-[3px] bg-accent-500" />

      {/*
        그 주를 한 줄로 요약한다. 아래로 스크롤하기 전에 "무슨 일이 있었나"가 먼저다.
        divide-x가 칸을 나눠서 배경을 여러 번 칠할 필요가 없다.

        <b>다섯 칸이 한계다.</b> 360px에서 한 칸이 65px밖에 안 남아 여섯 번째를 넣으면
        라벨이 줄바꿈된다. 더 보여줄 것이 생기면 여기 끼우지 말고 아래 구획으로 내려라.
        라벨을 두 글자 이상 늘리지도 마라 — "숙제 완료"가 "숙제"가 된 이유다.
      */}
      <div className="flex divide-x divide-white/10 bg-brand-950/50">
        {stats.map((stat) => (
          <div key={stat.label} className="flex-1 px-1 py-3 text-center">
            <p className="tnum text-base font-bold leading-none text-white">{stat.value}</p>
            <p className="mt-1.5 whitespace-nowrap text-[11px] text-brand-300">{stat.label}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
