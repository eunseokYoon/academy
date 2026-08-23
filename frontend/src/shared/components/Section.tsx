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
 * 하위 화면의 제목. <b>남색 띠 아래, 밝은 면 위에 앉는다.</b>
 *
 * <p>한동안 -mt-10으로 띠 안에 끌어올려 흰 글씨로 뒀었다. 그런데 제목 줄(26px)이
 * 띠의 pb-8(32px)에 위아래 3px씩밖에 안 남아 결국 <b>2px 삐져나와 경계에 걸쳤다.</b>
 * 띠를 키워 맞추는 대신 제목을 밝은 면으로 내리는 쪽을 골랐다(2026-08-18 확정).
 *
 * <p>그래서 <b>음수 마진이 없다.</b> 화면의 첫 요소가 아니어도 되고, 앱바의 pb-8을
 * 바꿔도 여기가 깨지지 않는다.
 *
 * <p>같은 자리를 채우는 BackLink도 같이 내려왔다. 한쪽만 흰 글씨로 남기면
 * 밝은 배경에서 통째로 안 보인다.
 */
export function PageTitle({
  children,
  action,
}: {
  children: ReactNode;
  /** 오른쪽 버튼·링크. 밝은 면 위에 놓이므로 남색 계열로 그려라. */
  action?: ReactNode;
}) {
  return (
    <div className="mb-4 flex min-h-[26px] items-center justify-between gap-3">
      <h2 className="min-w-0 truncate text-[19px] font-extrabold tracking-[-0.03em] text-brand-900">
        {children}
      </h2>
      {action}
    </div>
  );
}

/**
 * 상세 화면의 "← 목록" 줄. PageTitle과 같은 자리(밝은 면 위)에 앉는다.
 *
 * <p><b>색이 brand-600인 이유.</b> 제목과 같은 brand-900으로 두면 제목처럼 읽혀서
 * 눌러서 이동하는 곳으로 안 보인다. 이 앱에서 파랑은 "여기를 누르면 간다"는 뜻이다.
 */
export function BackLink({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <div className="mb-4 flex min-h-[26px] items-center">
      <button
        type="button"
        onClick={onClick}
        className="-ml-1 rounded-lg px-1 py-0.5 text-[13px] font-semibold text-brand-600
                   transition-colors hover:text-brand-800"
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
