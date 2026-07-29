import { Navigate } from "react-router-dom";
import { FullScreenLoader } from "../components/FullScreenLoader";
import { homePathOf, useAuth } from "./AuthContext";

/** 루트(/) 진입점. 역할별 홈으로 보낸다. */
export function RoleRedirect() {
  const { user, loading } = useAuth();

  if (loading) return <FullScreenLoader />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/password" replace />;
  return <Navigate to={homePathOf(user.role)} replace />;
}
