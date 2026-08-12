package com.njwenglish.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.common.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 액세스 토큰을 AuthUser로 복원한다.
 * 헤더가 없으면 그냥 통과시키고, 보호된 경로의 차단은 SecurityConfig가 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthUser authUser;
        try {
            authUser = jwtTokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
        } catch (BusinessException e) {
            // 만료·위조는 여기서 끝낸다. 컨트롤러까지 가지 않으므로 직접 JSON을 쓴다
            SecurityContextHolder.clearContext();
            ApiErrorWriter.write(response, objectMapper, e.getErrorCode());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(authUser, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role().name()))));
        filterChain.doFilter(request, response);
    }
}
