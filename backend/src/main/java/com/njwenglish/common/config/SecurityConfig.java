package com.njwenglish.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.common.security.JwtAuthenticationFilter;
import com.njwenglish.common.security.PasswordChangeRequiredFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 경로 접두사로 역할을 나눈다. /api/teacher/**에 학생 토큰으로 접근하면 403 ROLE_NOT_ALLOWED다.
 *
 * <p>경로 규칙은 역할만 본다. "남의 아이 데이터" 차단은 StudentAccessGuard의 몫이다.
 * 여기서 막았다고 서비스의 requireAccessible을 생략하지 마라.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/refresh",
                    "/api/auth/signup", "/api/health").permitAll()
                .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                .requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/parent/**").hasRole("PARENT")
                // 선생님은 알림을 받지 않는다 — 기기 토큰도 알림 설정도 없다
                .requestMatchers("/api/push/**").hasAnyRole("STUDENT", "PARENT")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler()))
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 토큰 없이 보호된 경로에 접근한 경우. 기본 응답은 형식이 달라 프론트 처리가 갈린다. */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> writeError(response, ErrorCode.TOKEN_INVALID);
    }

    /** 역할이 맞지 않는 경로에 접근한 경우. */
    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, deniedException) ->
            writeError(response, ErrorCode.ROLE_NOT_ALLOWED);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }
}
