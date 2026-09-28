package com.njwenglish.dto.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 토큰을 경로나 쿼리에 싣지 않는다 — 접근 로그에 남는다. 본문으로 받는다. */
public record DeviceTokenDeleteRequest(@NotBlank @Size(max = 4096) String token) {
}
