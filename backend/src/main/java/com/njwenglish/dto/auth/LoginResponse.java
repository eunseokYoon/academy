package com.njwenglish.dto.auth;

/** 리프레시 토큰은 여기 담기지 않는다. HttpOnly 쿠키로만 나간다. */
public record LoginResponse(String accessToken, UserSummaryResponse user) {
}
