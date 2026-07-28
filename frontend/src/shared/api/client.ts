import axios from "axios";
import type { AxiosError, InternalAxiosRequestConfig } from "axios";
import { getAccessToken, redirectToLogin, tryRefresh } from "../auth/token";
import type { ApiResponse } from "./types";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api",
});

api.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

// 401 → refresh 1회 재시도 → 실패 시 /login
// _retried 플래그가 없으면 리프레시도 401을 받을 때 무한 루프에 빠진다.
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    if (error.response?.status === 401 && config && !config._retried) {
      config._retried = true;
      if (await tryRefresh()) return api(config);
      redirectToLogin();
    }
    return Promise.reject(error);
  },
);

/** 응답 언래핑 헬퍼. 호출부에서 data.data를 반복하지 않게 한다. */
export async function get<T>(url: string, params?: object): Promise<T> {
  const res = await api.get<ApiResponse<T>>(url, { params });
  return res.data.data;
}

export async function post<T>(url: string, body?: object): Promise<T> {
  const res = await api.post<ApiResponse<T>>(url, body);
  return res.data.data;
}

export async function patch<T>(url: string, body?: object): Promise<T> {
  const res = await api.patch<ApiResponse<T>>(url, body);
  return res.data.data;
}

export async function del<T>(url: string): Promise<T> {
  const res = await api.delete<ApiResponse<T>>(url);
  return res.data.data;
}
