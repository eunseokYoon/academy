package com.njwenglish.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.entity.enums.UserRole;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 0000은 전원이 아는 값이다. 이 필터가 없으면 가입만 하고 방치한 계정으로
 * 반 친구가 로그인해 남의 성적·피드백을 본다.
 */
class PasswordChangeRequiredFilterTest {

    private final PasswordChangeRequiredFilter filter =
        new PasswordChangeRequiredFilter(new ObjectMapper());

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void login(boolean mustChangePassword) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthUser(10L, UserRole.STUDENT, mustChangePassword), null, java.util.List.of()));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    @DisplayName("변경 강제 상태에서 다른 API를 부르면 403 PASSWORD_CHANGE_REQUIRED다")
    void 다른_API는_막는다() throws ServletException, IOException {
        login(true);

        filter.doFilter(request("GET", "/api/student/homeworks"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
            .contains("PASSWORD_CHANGE_REQUIRED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("비밀번호 변경 요청은 통과시킨다")
    void 비밀번호_변경은_통과한다() throws ServletException, IOException {
        login(true);

        filter.doFilter(request("PATCH", "/api/auth/password"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("me와 logout은 통과시킨다")
    void me와_logout은_통과한다() throws ServletException, IOException {
        login(true);
        filter.doFilter(request("GET", "/api/auth/me"), response, new MockFilterChain());

        MockFilterChain logoutChain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/auth/logout"), response, logoutChain);

        assertThat(logoutChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("허용 경로라도 메서드가 다르면 막는다")
    void 메서드가_다르면_막는다() throws ServletException, IOException {
        login(true);

        filter.doFilter(request("POST", "/api/auth/password"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("비밀번호를 바꾼 사용자는 그대로 통과한다")
    void 정상_사용자는_통과한다() throws ServletException, IOException {
        login(false);

        filter.doFilter(request("GET", "/api/student/homeworks"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("비로그인 요청은 이 필터가 관여하지 않는다")
    void 비로그인은_통과한다() throws ServletException, IOException {
        filter.doFilter(request("POST", "/api/auth/login"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
