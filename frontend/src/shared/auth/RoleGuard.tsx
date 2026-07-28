import type { ReactNode } from "react";
import type { Role } from "../api/types";

interface Props {
  role: Role;
  children: ReactNode;
}

/**
 * Phase 0 통과용 스텁. 아직 로그인이 없어 검증하지 않고 그대로 렌더링한다.
 * Phase 2에서 토큰의 role 비교 + must_change_password 처리로 교체한다.
 */
export function RoleGuard({ children }: Props) {
  return <>{children}</>;
}
