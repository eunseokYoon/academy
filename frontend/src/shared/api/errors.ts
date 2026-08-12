import { AxiosError } from "axios";
import type { ApiResponse } from "./types";

/** 서버가 이미 한국어 메시지를 내려준다. 화면에서 코드별 문구를 다시 짜지 않는다. */
export function errorMessage(error: unknown, fallback = "요청을 처리하지 못했습니다."): string {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiResponse<unknown> | undefined;
    if (body?.error?.message) return body.error.message;
  }
  return fallback;
}

export function errorCode(error: unknown): string | null {
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiResponse<unknown> | undefined;
    return body?.error?.code ?? null;
  }
  return null;
}
