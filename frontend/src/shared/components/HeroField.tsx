import type { ReactNode } from "react";

export interface HeroStat {
  label: string;
  /** 숫자가 주인공이다. "D-3" · "2" · "08/15"처럼 짧게 넣어라 — 문장을 넣으면 칸이 깨진다. */
  value: string;
  /** 값 아래 한 줄. 없으면 칸 높이가 줄어드는 게 아니라 그냥 빈다. */
  sub?: string | null;
  /**
   * 같은 칸 안의 둘째 줄. <b>칸을 넷으로 늘리는 대신 쓴다</b> — 360px에서 칸이 넷이면
   * "D-61"이 줄바꿈된다. 다음 수업과 다음 클리닉처럼 <b>같은 종류의 일정</b>일 때만 묶어라.
   * 성격이 다른 값(숙제 개수와 시험 D-day)을 한 칸에 넣으면 라벨을 둘 다 읽어야 한다.
   */
  extra?: { label: string; value: string; sub?: string | null };
  /**
   * 주황 칸. <b>한 화면에 하나만.</b> 둘이 되면 어느 쪽이 급한지 알 수 없다.
   * "학생이 아직 처리 안 한 것"에만 붙인다 — tailwind.config의 accent 주석을 봐라.
   */
  hot?: boolean;
}

interface Props {
  /** 값 위의 작은 회색 줄. 보통 오늘 날짜다. */
  eyebrow?: string;
  /** 인사말. 두 줄까지 자연스럽다. */
  title: ReactNode;
  /**
   * 최대 3개. 넷을 넘기지 마라 — 360px에서 "D-61"이 줄바꿈되고,
   * 넷째부터는 어차피 아무도 안 본다. 값이 없는 항목은 <b>빼라</b>.
   * null을 "미정"으로 채우면 "시험이 오늘"처럼 읽히는 칸이 생긴다.
   * 칸 하나에 일정 둘을 묶고 싶으면 `extra`를 써라 — 넷째 칸을 만들지 마라.
   */
  stats?: HeroStat[];
  /** 지면 안쪽, 숫자 칸 아래. 학부모 홈의 자녀 선택이 여기 들어간다. */
  children?: ReactNode;
}

/**
 * 홈 화면 위쪽의 남색 지면. 앱바 띠가 그대로 아래로 이어져 하나로 읽힌다.
 *
 * <p><b>왜 있나.</b> 예전에는 화면 전체가 흰 카드 한 종류뿐이라 위계가 없었다.
 * 인사말 카드와 숙제 카드가 같은 흰색·같은 그림자·같은 반경이어서 어디가 본론인지
 * 색으로는 읽히지 않았다. 표면을 둘로 나누면 그 아래 카드가 "떠 있는 것"이 된다.
 *
 * <p><b>바로 뒤에 오는 카드는 `hero-lift`를 붙여야 한다.</b> 지면의 pb-16이 그 카드가
 * 걸터앉을 자리다. 안 붙이면 남색 아래에 빈 띠가 남는다.
 *
 * <p>가로로 화면 폭 전체를 먹는 건 `.field`가 한다 — index.css의 주석을 봐라.
 * 부모 레이아웃의 `overflow-x-clip`이 짝이다.
 */
export function HeroField({ eyebrow, title, stats, children }: Props) {
  return (
    <section className="field">
      {eyebrow && <p className="text-[13px] font-medium text-brand-200/80">{eyebrow}</p>}

      <h2 className="mt-0.5 text-[25px] font-extrabold leading-[1.28] tracking-[-0.035em]">
        {title}
      </h2>

      {stats && stats.length > 0 && (
        <div className="mt-4 flex gap-2.5">
          {stats.map((stat) => (
            <div key={stat.label} className={`stat ${stat.hot ? "stat-hot" : ""}`}>
              <p
                className={`text-[10.5px] font-semibold tracking-[0.04em] ${
                  stat.hot ? "text-accent-200" : "text-brand-200/75"
                }`}
              >
                {stat.label}
              </p>
              <p
                className={`tnum mt-1 truncate text-[21px] font-extrabold tracking-[-0.03em] ${
                  stat.hot ? "text-accent-100" : "text-white"
                }`}
              >
                {stat.value}
              </p>
              {stat.sub && (
                <p className="tnum mt-0.5 truncate text-[10.5px] text-brand-200/55">{stat.sub}</p>
              )}
              {/*
                구분선이 있어야 두 일정이 한 칸에서 서로 다른 것으로 읽힌다.
                없으면 라벨 두 개와 숫자 두 개가 네 줄로 흘러 무엇이 무엇인지 모른다.
                주황 칸에서는 쓰지 않으므로 색은 남색 계열 하나로 충분하다.
              */}
              {stat.extra && (
                <>
                  <div className="mt-2 border-t border-brand-200/25" />
                  <p className="mt-2 text-[10.5px] font-semibold tracking-[0.04em]
                                text-brand-200/75">
                    {stat.extra.label}
                  </p>
                  <p className="tnum mt-1 truncate text-[21px] font-extrabold
                                tracking-[-0.03em] text-white">
                    {stat.extra.value}
                  </p>
                  {stat.extra.sub && (
                    <p className="tnum mt-0.5 truncate text-[10.5px] text-brand-200/55">
                      {stat.extra.sub}
                    </p>
                  )}
                </>
              )}
            </div>
          ))}
        </div>
      )}

      {children}
    </section>
  );
}
