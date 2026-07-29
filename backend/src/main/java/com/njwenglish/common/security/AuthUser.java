package com.njwenglish.common.security;

import com.njwenglish.entity.enums.UserRole;

/**
 * 액세스 토큰에서 복원한 로그인 사용자. Authentication의 principal로 들어간다.
 *
 * <p>mustChangePassword를 토큰에 담는 이유는 요청마다 users를 다시 읽지 않기 위해서다.
 * 비밀번호를 바꾸면 리프레시 토큰이 전부 폐기되고 다시 로그인하므로 값이 오래 남지 않는다.
 */
public record AuthUser(Long userId, UserRole role, boolean mustChangePassword) {
}
