package com.njwenglish.dto.auth;

/**
 * 서비스 → 컨트롤러 내부 전달용. 직렬화되지 않는다.
 *
 * <p>리프레시 토큰은 응답 본문이 아니라 Set-Cookie로 나가야 하는데, 서비스는 웹 타입을
 * 만지지 않는다. 그래서 본문(body)과 쿠키에 담을 값(refreshToken)을 나눠 돌려준다.
 */
public record LoginResult(LoginResponse body, String refreshToken) {
}
