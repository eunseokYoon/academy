const ACCESS_TOKEN_KEY = "academy.accessToken";

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

/**
 * Phase 2에서 POST /api/auth/refresh 호출로 교체한다.
 * 지금은 항상 실패시켜 로그인으로 보낸다.
 */
export async function tryRefresh(): Promise<boolean> {
  return false;
}

export function redirectToLogin(): void {
  clearAccessToken();
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}
