import { Link } from "react-router-dom";
import { Badge } from "./Badge";
import { Icon } from "./Icon";
import type { IconName } from "./Icon";

interface Props {
  to: string;
  icon: IconName;
  label: string;
  /** 타일 안에 뭐가 있는지 한 줄로. 라벨만 있으면 눌러보기 전엔 알 수 없다. */
  sub: string;
  badge?: string | null;
}

/** 홈 화면 2열 격자의 한 칸. 참고 디자인의 아이콘 타일 자리다. */
export function MenuTile({ to, icon, label, sub, badge }: Props) {
  return (
    <Link to={to} className="tile">
      <span className="flex items-start justify-between">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand-50 text-brand-600">
          <Icon name={icon} />
        </span>
        {badge && <Badge tone="brand">{badge}</Badge>}
      </span>
      <span className="mt-3 text-sm font-bold text-brand-900">{label}</span>
      <span className="mt-0.5 text-xs leading-relaxed text-slate-500">{sub}</span>
    </Link>
  );
}
