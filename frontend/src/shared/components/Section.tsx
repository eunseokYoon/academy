import { Link } from "react-router-dom";
import type { ReactNode } from "react";

/**
 * 구획의 색. <b>세 가지가 전부다.</b> 네 번째를 만들지 마라 —
 * 색이 넷이 되는 순간 "이 색은 무슨 뜻이지"가 생기고, 그때부터 색이 정보를 못 준다.
 *
 * <ul>
 *   <li>accent(주황) — <b>학생이 아직 처리 안 한 것.</b> 미완료 숙제, 재제출.
 *       tailwind.config의 accent 주석이 정본이다. 여기 말고 다른 구획에 쓰지 마라.
 *   <li>brand(남색) — 읽을 것·기록. 공지, 시험 일정, 성적, 출석.
 *   <li>neutral(회색) — 곁다리. 지난 수업, 자료실처럼 급하지 않은 것.
 * </ul>
 */
export type SectionTone = "accent" | "brand" | "neutral";

const BAR: Record<SectionTone, string> = {
  accent: "bg-accent-500",
  brand: "bg-brand-600",
  neutral: "bg-slate-300",
};

const COUNT: Record<SectionTone, string> = {
  accent: "bg-accent-500",
  brand: "bg-brand-600",
  neutral: "bg-slate-400",
};

/**
 * 틴트 블록의 세 값이 한 벌이다 — 왼쪽 바 / 배경 면 / 행 사이 선.
 * 셋 중 하나만 바꾸면 블록이 지저분해진다. 같이 고쳐라.
 */
const BLOCK: Record<SectionTone, string> = {
  accent: "border-l-accent-500 bg-accent-50 [&>*+*]:border-accent-100",
  brand: "border-l-brand-600 bg-brand-50 [&>*+*]:border-brand-100",
  neutral: "border-l-slate-300 bg-white [&>*+*]:border-slate-100",
};

/**
 * 하위 화면의 제목. <b>남색 띠 안에 들어앉는다.</b>
 *
 * <p>예전에는 회색 배경 위 검은 글씨였고, 그 위로 앱바의 pb-8(32px)이 아무것도 없는
 * 남색 띠로 남아 있었다. 화면마다 죽은 32px이 있었다는 뜻이다. 제목을 그 자리로
 * 끌어올리면 띠가 제목 줄이 되고, 홈의 지면과 같은 말을 하게 된다.
 *
 * <p><b>-mt-10(hero-lift)이 정확히 그 자리다.</b> main의 pt-4(16px)에서 40px을 빼면
 * 띠 바닥에서 24px 위 — 32px 띠 안쪽 8px 여백이다. 앱바의 pb-8을 바꾸면 여기도 깨진다.
 *
 * <p>제목이 화면의 첫 요소여야 한다. 뒤에 두면 음수 마진이 앞 카드를 덮는다.
 */
export function PageTitle({
  children,
  action,
}: {
  children: ReactNode;
  /** 오른쪽 버튼·링크. 남색 위에 놓이므로 흰 계열로 그려라. */
  action?: ReactNode;
}) {
  return (
    <div className="hero-lift mb-4 flex min-h-[26px] items-center justify-between gap-3">
      <h2 className="min-w-0 truncate text-[19px] font-extrabold tracking-[-0.03em] text-white">
        {children}
      </h2>
      {action}
    </div>
  );
}

/**
 * 상세 화면의 "← 목록" 줄. PageTitle과 같은 자리(남색 띠 안)에 앉는다.
 *
 * <p>목록 화면은 PageTitle이 띠를 채우고 상세 화면은 이게 채운다. 둘 중 하나가 빠지면
 * 그 화면만 위에 남색 공백이 뜬다 — 상세로 들어갔다 나올 때 화면이 덜컹거리는 원인이었다.
 */
export function BackLink({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <div className="hero-lift mb-4 flex min-h-[26px] items-center">
      <button
        type="button"
        onClick={onClick}
        className="-ml-1 rounded-lg px-1 py-0.5 text-[13px] font-semibold text-white/85
                   transition-colors hover:text-white"
      >
        ← {children}
      </button>
    </div>
  );
}

interface HeadProps {
  tone?: SectionTone;
  title: string;
  /**
   * 제목 옆 알약. <b>0이면 넘기지 마라</b> — 회색 0은 "없음"을 굳이 강조한다.
   * undefined면 알약 자체가 안 그려진다.
   */
  count?: number;
  /** 오른쪽 링크. 없으면 안 그린다. */
  to?: string;
  actionLabel?: string;
}

/**
 * 구획 제목 줄. 컬러 바 + 제목 + 개수 알약 + 오른쪽 링크.
 *
 * <p><b>이게 화면의 리듬을 만든다.</b> 예전에는 구획 제목이 회색 작은 글씨 하나뿐이라
 * 흰 카드가 세로로 쌓이면 어디서 구획이 끊기는지 <b>글자를 읽어야</b> 알 수 있었다.
 * 왼쪽 4px 바 하나로 그게 색이 된다.
 *
 * <p>개수를 알약으로 올리는 이유는, 아래 목록을 세지 않아도 "몇 개 남았나"가
 * 제목 줄에서 끝나기 때문이다.
 */
export function SectionHead({ tone = "brand", title, count, to, actionLabel = "전체" }: HeadProps) {
  return (
    <div className="mb-2 flex items-center gap-2 pl-0.5">
      <span aria-hidden="true" className={`h-[15px] w-1 shrink-0 rounded-sm ${BAR[tone]}`} />
      <h3 className="text-sm font-extrabold tracking-[-0.02em] text-brand-900">{title}</h3>
      {count != null && count > 0 && (
        <span
          className={`tnum min-w-[20px] rounded-full px-1.5 py-px text-center text-[11px]
                      font-bold text-white ${COUNT[tone]}`}
        >
          {count}
        </span>
      )}
      {to && (
        <Link to={to} className="ml-auto text-[11.5px] font-semibold text-slate-500">
          {actionLabel} ›
        </Link>
      )}
    </div>
  );
}

/**
 * 목록 한 덩어리. 흰 카드 여러 장 대신 <b>틴트 블록 한 장</b>이다.
 *
 * <p>카드를 낱장으로 두면 항목이 6개일 때 같은 그림자가 6번 반복돼 리듬이 사라진다.
 * 한 덩어리로 묶고 안쪽은 헤어라인으로만 끊으면, 그림자는 한 번이고 구획이 하나로 읽힌다.
 *
 * <p>자식 사이 선은 <b>직접 컴포넌트가 건다</b>(`[&>*+*]:border-t`). 자식이 li든 a든
 * div든 상관없이 먹으라고 이렇게 했다 — divide-y는 자식 타입을 타서 목록마다 어긋났다.
 */
export function TintBlock({
  tone = "brand",
  className = "",
  children,
}: {
  tone?: SectionTone;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      className={`overflow-hidden rounded-2xl border-l-4 shadow-card
                  [&>*+*]:border-t ${BLOCK[tone]} ${className}`}
    >
      {children}
    </div>
  );
}
