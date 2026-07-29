package com.njwenglish.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.entity.enums.UserRole;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "academy-local-test-secret-key-academy-local-test-secret-key";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 1800, 1209600);
    private final JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(provider, new ObjectMapper());

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 토큰이면 SecurityContext에 역할과 함께 담긴다")
    void 유효한_토큰은_인증된다() throws ServletException, IOException {
        request.addHeader("Authorization",
            "Bearer " + provider.createAccessToken(1L, UserRole.TEACHER, false));

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthUser(1L, UserRole.TEACHER, false));
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
            .containsExactly("ROLE_TEACHER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("헤더가 없으면 인증 없이 통과시킨다")
    void 헤더가_없으면_통과한다() throws ServletException, IOException {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("만료된 토큰이면 401 TOKEN_EXPIRED JSON을 쓰고 체인을 끊는다")
    void 만료된_토큰은_401이다() throws ServletException, IOException {
        String expired = new JwtTokenProvider(SECRET, -60, 1209600)
            .createAccessToken(1L, UserRole.TEACHER, false);
        request.addHeader("Authorization", "Bearer " + expired);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("\"success\":false")
            .contains("TOKEN_EXPIRED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("서명이 깨진 토큰이면 401 TOKEN_INVALID다")
    void 잘못된_토큰은_401이다() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer 아무말이나");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
            .contains("TOKEN_INVALID");
        assertThat(chain.getRequest()).isNull();
    }
}
