import { ACADEMY_NAME, ACADEMY_NAME_HEAD, ACADEMY_NAME_TAIL } from "../branding";

/**
 * NJ 모노그램. 흰 N의 오른쪽 기둥을 주황 J가 대신하는 로고의 도형이다.
 *
 * <p><b>N의 오른쪽 세로획을 따로 그리지 마라.</b> N이 J에 기대어 서는 것이 이 마크의
 * 전부다 — 획을 하나 더 그으면 그냥 N 옆에 J가 선 모양이 된다.
 *
 * <p>색을 currentColor로 두지 않았다. 남색 앱바 위와 흰 파비콘 위 어디서든
 * 흰 N + 주황 J로 같아야 로고로 읽힌다.
 */
export function LogoMark({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      className={className}
      fill="none"
      strokeWidth="3"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {/* N — 왼쪽 기둥에서 올라가 사선으로 내려온다. 오른쪽 기둥은 J가 맡는다 */}
      <path d="M7 17.5V6.5L16 16.5" stroke="#FFFFFF" />
      {/* J — 기둥을 세우고 아래에서 왼쪽으로 갈고리를 건다 */}
      <path d="M17 6v9.2c0 1.9-1.6 3.2-3.5 2.8" stroke="#D9542B" />
    </svg>
  );
}

/**
 * 마크를 흰 테두리 사각형 안에 넣은 배지.
 *
 * <p><b>왜 테두리가 필요한가.</b> 마크는 획만 있는 도형이라(면이 없다) 남색 위에 그대로
 * 얹으면 흰 획 두 개가 배경에 떠 있는 꼴이 된다 — 로고가 아니라 장식으로 읽힌다.
 * 테두리가 마크의 <b>가장자리</b>를 만들어 주면 그제서야 하나의 물건이 된다.
 *
 * <p>안을 흰색으로 채우지 않는다. 채우면 남색 위에 흰 스티커를 붙인 꼴이고,
 * 흰 판 위에서 N이 안 보여 마크를 남색으로 뒤집어야 한다 — 색 잠금이 둘로 갈라진다.
 * 테두리만 두면 마크는 어디서나 흰 N + 주황 J 하나로 유지된다.
 *
 * <p><b>남색 배경 전용이다.</b> 흰 배경에 놓으면 테두리도 마크도 보이지 않는다.
 * 흰 바탕에는 파비콘처럼 남색 판을 쓴다(public/favicon.svg).
 */
export function LogoBadge({
  className = "h-[30px] w-[30px] rounded-[9px] border-[1.5px]",
  markClassName = "h-[19px] w-[19px]",
}: {
  /** 배지 크기·모서리·테두리 두께. 크기를 키우면 반경과 두께도 같이 올려라 */
  className?: string;
  /** 안에 들어가는 마크 크기. 배지의 60~65%가 적당하다 — 꽉 채우면 획이 테두리에 붙는다 */
  markClassName?: string;
}) {
  return (
    <span
      aria-hidden="true"
      className={`grid shrink-0 place-items-center border-white/85 ${className}`}
    >
      <LogoMark className={markClassName} />
    </span>
  );
}

/**
 * 앱바의 워드마크. 로고와 같은 잠금 방식이다 — 한글은 흰색, LAB만 주황.
 *
 * <p>이름을 통째로 흰색으로 두면 로고와 다른 물건으로 보인다.
 * 주황 두 글자가 앱바와 로고를 같은 것으로 묶는다.
 */
export function Wordmark() {
  return (
    <span className="truncate text-[15px] font-extrabold tracking-[-0.02em]">
      {ACADEMY_NAME_HEAD}
      <span className="text-accent-500">{ACADEMY_NAME_TAIL}</span>
      <span className="sr-only">{ACADEMY_NAME}</span>
    </span>
  );
}
