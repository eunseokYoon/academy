import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import type { Role } from "../api/types";
import { FullScreenLoader } from "../components/FullScreenLoader";
import { homePathOf, useAuth } from "./AuthContext";

interface Props {
  role: Role;
  children: ReactNode;
}

/**
 * 화면을 숨기는 것은 UX일 뿐이다. 실제 차단은 서버에서만 유효하다.
 * 여기서 막았다고 백엔드의 권한 검증을 생략하지 마라.
 */
export function RoleGuard({ role, children }: Props) {
  const { user, loading } = useAuth();

  if (loading) return <FullScreenLoader />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) return <Navigate to={homePathOf(user.role)} replace />;
  if (user.mustChangePassword) return <Navigate to="/password" replace />;
  return <>{children}</>;
}

/** 역할을 가리지 않고 로그인만 요구한다. 비밀번호 변경 화면(C-2)이 쓴다. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) return <FullScreenLoader />;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
