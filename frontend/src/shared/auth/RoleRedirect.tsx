import { Navigate } from "react-router-dom";

/**
 * Phase 2에서 로그인한 사용자의 역할에 따라
 * /student · /parent · /teacher로 보내도록 교체한다.
 * 지금은 로그인 상태가 없으므로 항상 /login이다.
 */
export function RoleRedirect() {
  return <Navigate to="/login" replace />;
}
