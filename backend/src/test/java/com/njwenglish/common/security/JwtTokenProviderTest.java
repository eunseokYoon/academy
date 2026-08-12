package com.njwenglish.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.entity.enums.UserRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "academy-local-test-secret-key-academy-local-test-secret-key";
    private static final String OTHER_SECRET = "another-secret-key-another-secret-key-another-secret-key-xx";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 1800, 1209600);

    @Test
    @DisplayName("액세스 토큰을 파싱하면 발급한 사용자 정보가 그대로 나온다")
    void 액세스_토큰_왕복() {
        String token = provider.createAccessToken(12L, UserRole.PARENT, true);

        AuthUser parsed = provider.parseAccessToken(token);

        assertThat(parsed.userId()).isEqualTo(12L);
        assertThat(parsed.role()).isEqualTo(UserRole.PARENT);
        assertThat(parsed.mustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("만료된 액세스 토큰은 TOKEN_EXPIRED다")
    void 만료된_토큰은_TOKEN_EXPIRED() {
        JwtTokenProvider expired = new JwtTokenProvider(SECRET, -60, 1209600);
        String token = expired.createAccessToken(12L, UserRole.STUDENT, false);

        assertThatThrownBy(() -> provider.parseAccessToken(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 TOKEN_INVALID다")
    void 서명이_다르면_TOKEN_INVALID() {
        String forged = new JwtTokenProvider(OTHER_SECRET, 1800, 1209600)
            .createAccessToken(12L, UserRole.TEACHER, false);

        assertThatThrownBy(() -> provider.parseAccessToken(forged))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("리프레시 토큰을 파싱하면 사용자 id가 나온다")
    void 리프레시_토큰_왕복() {
        String token = provider.createRefreshToken(88L);

        assertThat(provider.parseRefreshToken(token)).isEqualTo(88L);
    }

    @Test
    @DisplayName("리프레시 토큰은 매번 다른 값이다")
    void 리프레시_토큰은_매번_다르다() {
        assertThat(provider.createRefreshToken(88L))
            .isNotEqualTo(provider.createRefreshToken(88L));
    }

    @Test
    @DisplayName("토큰의 만료 시각을 읽어 refresh_tokens에 그대로 저장할 수 있다")
    void 만료_시각을_읽는다() {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        String token = provider.createRefreshToken(88L);

        assertThat(provider.expiresAt(token)).isBetween(
            issuedAt.plusSeconds(1209600 - 5), issuedAt.plusSeconds(1209600 + 5));
    }

    @Test
    @DisplayName("만료된 리프레시 토큰은 TOKEN_EXPIRED다")
    void 만료된_리프레시_토큰은_TOKEN_EXPIRED() {
        String token = new JwtTokenProvider(SECRET, 1800, -60).createRefreshToken(88L);

        assertThatThrownBy(() -> provider.parseRefreshToken(token))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }
}
