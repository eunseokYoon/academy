import axios from "axios";
import type { ApiResponse } from "../api/types";

/**
 * 액세스 토큰은 메모리에만 둔다. localStorage에 넣으면 XSS 한 번에 털린다.
 * 새로고침하면 사라지고, HttpOnly 쿠키의 리프레시 토큰으로 복구한다.
 */
let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

const baseURL = import.meta.env.VITE_API_BASE_URL ?? "/api";

/**
 * 리프레시는 axios 기본 인스턴스로 부른다. api 인스턴스를 쓰면 이 요청이 받은 401이
 * 다시 인터셉터를 타서 무한 루프가 된다.
 *
 * <p>동시에 여러 요청이 401을 받아도 리프레시는 한 번만 나가도록 묶어 둔다.
 */
let inFlight: Promise<boolean> | null = null;

export function tryRefresh(): Promise<boolean> {
  if (!inFlight) {
    inFlight = axios
      .post<ApiResponse<{ accessToken: string }>>(`${baseURL}/auth/refresh`, null, {
        withCredentials: true,
      })
      .then((res) => {
        setAccessToken(res.data.data.accessToken);
        return true;
      })
      .catch(() => {
        clearAccessToken();
        return false;
      })
      .finally(() => {
        inFlight = null;
      });
  }
  return inFlight;
}

export function redirectToLogin(): void {
  clearAccessToken();
  if (window.location.pathname !== "/login") {
    window.location.href = "/login";
  }
}
