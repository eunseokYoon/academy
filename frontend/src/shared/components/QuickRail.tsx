import { Link } from "react-router-dom";
import { Icon } from "./Icon";
import type { IconName } from "./Icon";

export interface QuickItem {
  to: string;
  icon: IconName;
  label: string;
  /** 0보다 크면 칸 위에 점이 붙는다. 밀린 게 있다는 신호이고 숫자는 쓰지 않는다. */
  count?: number;
  /**
   * 남색으로 채운 칸. <b>레일당 하나만.</b> 둘이 되면 "여기부터 보면 된다"가 사라지고
   * 여덟 칸이 도로 똑같아진다.
   *
   * <p>이게 없을 때가 원래 문제였다 — 옅은 칸 여덟 개가 가로로 늘어서면 아이콘 모양만
   * 다르고 무게가 전부 같아서, 어디를 눌러야 하는지가 라벨을 읽어야만 나왔다.
   */
  primary?: boolean;
}

/**
 * 홈 화면의 가로 퀵 레일. <b>홈을 뺀 모든 화면이 여기 다 있다.</b>
 *
 * <p>하단 탭 바와 겹치는 항목이 있는 건 의도다. 하단 바는 자주 쓰는 네 곳의 빠른 길이고,
 * 이 레일은 "전부 한눈에"가 목적이라 역할이 다르다. 겹친다고 빼면 레일이 남은 것들의
 * 잡동사니가 된다.
 *
 * <p><b>마지막 칸이 화면 밖으로 반쯤 나가야 한다.</b> 폭에 딱 맞으면 옆으로 넘길 수 있다는
 * 걸 아무도 모른다 — 잘린 원이 이 레일의 유일한 어포던스다. 그래서 카드 안쪽 여백을
 * 음수 마진으로 뚫고 나가고(-mx-4), 오른쪽에는 여백 대신 스크롤 끝 패딩(pr-4)만 둔다.
 * 항목이 적어 안 넘칠 때는 그냥 왼쪽 정렬로 서고 아무 문제 없다.
 *
 * <p><b>위쪽 여백은 여기서 주지 않는다.</b> 홈에서는 이 레일이 카드 하나를 통째로 채우고
 * 그 카드가 지면 위로 올라타므로(hero-lift), 레일이 mt를 갖고 있으면 카드 안이 위아래로
 * 어긋난다. 간격은 감싸는 쪽이 정한다.
 */
export function QuickRail({ items }: { items: QuickItem[] }) {
  return (
    <nav
      aria-label="바로가기"
      className="no-scrollbar -mx-4 flex snap-x snap-mandatory gap-1 overflow-x-auto px-4 pb-1"
    >
      {items.map((item) => (
        <Link
          key={item.to}
          to={item.to}
          className="group flex w-[68px] shrink-0 snap-start flex-col items-center gap-1.5
                     rounded-xl py-1 transition-transform active:scale-95"
        >
          <span className="relative">
            {/*
              원이 아니라 모난 사각형이다. 원 여덟 개는 아이콘이 달라도 실루엣이 같아서
              가로로 늘어놓으면 한 덩어리로 뭉쳐 보인다.
            */}
            <span
              className={`grid h-[52px] w-[52px] place-items-center rounded-[16px]
                          transition-colors ${
                            item.primary
                              ? "bg-brand-900 text-white group-hover:bg-brand-800"
                              : "bg-brand-50 text-brand-600 ring-1 ring-inset ring-brand-100 group-hover:bg-brand-100"
                          }`}
            >
              <Icon name={item.icon} className="h-[22px] w-[22px]" />
            </span>
            {/*
              숫자가 아니라 점이다. 52px 원 위에 "3"을 얹으면 읽으려고 눈이 멈추는데,
              여기서 알아야 하는 건 개수가 아니라 "볼 게 있다"뿐이다. 개수는 들어가서 본다.

              빨강이 아니라 로고의 주황이다. 빨강은 이 앱에서 "결석·위험"만 뜻하기로
              해 뒀는데 안 낸 숙제 알림은 위험이 아니다.
            */}
            {item.count != null && item.count > 0 && (
              <span
                aria-hidden="true"
                className="absolute right-0.5 top-0.5 h-2.5 w-2.5 rounded-full bg-accent-500
                           ring-2 ring-white"
              />
            )}
          </span>
          <span className="text-center text-[11px] font-medium leading-tight text-brand-900">
            {item.label}
          </span>
        </Link>
      ))}
    </nav>
  );
}
