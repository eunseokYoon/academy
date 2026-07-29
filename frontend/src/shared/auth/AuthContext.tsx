import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { get, post } from "../api/client";
import type { Role } from "../api/types";
import { clearAccessToken, setAccessToken, tryRefresh } from "./token";
import { digitsOnly } from "../lib/phone";

export interface AuthUser {
  id: number;
  name: string;
  role: Role;
  phone: string;
  mustChangePassword: boolean;
}

interface LoginResponse {
  accessToken: string;
  user: { id: number; name: string; role: Role; mustChangePassword: boolean };
}

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  signIn: (loginId: string, password: string) => Promise<AuthUser>;
  signOut: () => Promise<void>;
  /** 서버 호출 없이 화면 상태만 비운다. 이미 토큰이 폐기된 뒤(비밀번호 변경)에 쓴다. */
  resetSession: () => void;
  reload: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function homePathOf(role: Role): string {
  if (role === "STUDENT") return "/student";
  if (role === "PARENT") return "/parent";
  return "/teacher";
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setUser(await get<AuthUser>("/auth/me"));
  }, []);

  /**
   * 앱 부팅: refresh 시도 → 성공하면 me로 상태 복구, 실패하면 비로그인.
   * 이 판정이 끝나기 전에는 loading이라 라우팅하지 않는다. 안 그러면 로그인 화면이 깜빡인다.
   */
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        if (await tryRefresh()) {
          const me = await get<AuthUser>("/auth/me");
          if (alive) setUser(me);
        }
      } catch {
        clearAccessToken();
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  const signIn = useCallback(async (loginId: string, password: string) => {
    const result = await post<LoginResponse>("/auth/login", {
      loginId: digitsOnly(loginId),
      password,
    });
    setAccessToken(result.accessToken);
    const me = await get<AuthUser>("/auth/me");
    setUser(me);
    return me;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await post<void>("/auth/logout");
    } finally {
      clearAccessToken();
      setUser(null);
    }
  }, []);

  const resetSession = useCallback(() => {
    clearAccessToken();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, signIn, signOut, resetSession, reload }),
    [user, loading, signIn, signOut, resetSession, reload],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  return context;
}
