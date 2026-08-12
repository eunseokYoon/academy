package com.njwenglish.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 초기 비밀번호(0000) 상태에서는 비밀번호 변경 외 어떤 API도 호출할 수 없다.
 *
 * <p>0000은 전원이 아는 값이고 반 친구끼리는 서로 전화번호를 안다. 이 필터가 없으면
 * 가입 후 며칠 방치된 계정으로 남의 성적·피드백·출석이 그대로 새어 나간다.
 * 이러면 남이 로그인해도 비밀번호 변경 화면 말고는 아무것도 보지 못한다.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    /** 허용 경로는 이 셋뿐이다. 늘리지 마라. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "/api/auth/password", Set.of("PATCH"),
        "/api/auth/me", Set.of("GET"),
        "/api/auth/logout", Set.of("POST"));

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (mustChangePassword() && !isAllowed(request)) {
            ApiErrorWriter.write(response, objectMapper, ErrorCode.PASSWORD_CHANGE_REQUIRED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean mustChangePassword() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user
            && user.mustChangePassword();
    }

    private boolean isAllowed(HttpServletRequest request) {
        return ALLOWED.getOrDefault(request.getRequestURI(), Set.of())
            .contains(request.getMethod());
    }
}
