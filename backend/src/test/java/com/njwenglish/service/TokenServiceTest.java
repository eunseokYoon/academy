package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.JwtTokenProvider;
import com.njwenglish.entity.RefreshToken;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.RefreshTokenRepository;
import com.njwenglish.support.Fixtures;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String SECRET = "academy-local-test-secret-key-academy-local-test-secret-key";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(refreshTokenRepository,
            new JwtTokenProvider(SECRET, 1800, 1209600));
        user = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
    }

    @Test
    @DisplayName("발급하면 원문이 아니라 해시가 저장된다")
    void 원문이_아니라_해시를_저장한다() {
        String raw = tokenService.issue(user);

        assertThat(savedToken().getTokenHash())
            .isNotBlank()
            .isNotEqualTo(raw);
    }

    @Test
    @DisplayName("발급한 토큰을 검증하면 사용자가 나온다")
    void 발급한_토큰은_검증된다() {
        String raw = tokenService.issue(user);
        RefreshToken saved = savedToken();
        given(refreshTokenRepository.findByTokenHash(saved.getTokenHash()))
            .willReturn(Optional.of(saved));

        assertThat(tokenService.validate(raw)).isSameAs(user);
    }

    @Test
    @DisplayName("폐기된 토큰은 TOKEN_EXPIRED다")
    void 폐기된_토큰은_거부한다() {
        String raw = tokenService.issue(user);
        RefreshToken saved = savedToken();
        saved.revoke(OffsetDateTime.now());
        given(refreshTokenRepository.findByTokenHash(saved.getTokenHash()))
            .willReturn(Optional.of(saved));

        assertThatThrownBy(() -> tokenService.validate(raw))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("DB에 없는 토큰은 TOKEN_EXPIRED다")
    void 저장되지_않은_토큰은_거부한다() {
        String raw = tokenService.issue(user);
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.validate(raw))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("만료된 토큰은 TOKEN_EXPIRED다")
    void 만료된_토큰은_거부한다() {
        String expired = new JwtTokenProvider(SECRET, 1800, -60).createRefreshToken(10L);

        assertThatThrownBy(() -> tokenService.validate(expired))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("로그아웃하면 그 토큰만 폐기된다")
    void 로그아웃은_해당_토큰을_폐기한다() {
        String raw = tokenService.issue(user);
        RefreshToken saved = savedToken();
        given(refreshTokenRepository.findByTokenHash(saved.getTokenHash()))
            .willReturn(Optional.of(saved));

        tokenService.revoke(raw);

        assertThat(saved.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("비밀번호 변경 시 그 사용자의 살아 있는 토큰이 전부 폐기된다")
    void 사용자의_모든_토큰을_폐기한다() {
        tokenService.issue(user);
        RefreshToken first = savedToken();
        given(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(10L))
            .willReturn(List.of(first));

        tokenService.revokeAllOf(10L);

        assertThat(first.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 없는 토큰으로 로그아웃해도 예외를 던지지 않는다")
    void 없는_토큰_로그아웃은_조용히_넘어간다() {
        given(refreshTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

        tokenService.revoke(tokenService.issue(user));
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }
}
