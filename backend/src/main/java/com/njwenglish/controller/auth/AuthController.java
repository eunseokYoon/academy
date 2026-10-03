package com.njwenglish.controller.auth;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.auth.AccountDeleteRequest;
import com.njwenglish.dto.auth.LoginRequest;
import com.njwenglish.dto.auth.LoginResponse;
import com.njwenglish.dto.auth.LoginResult;
import com.njwenglish.dto.auth.MeResponse;
import com.njwenglish.dto.auth.PasswordChangeRequest;
import com.njwenglish.dto.auth.SignupRequest;
import com.njwenglish.dto.auth.SignupResponse;
import com.njwenglish.dto.auth.TokenResponse;
import com.njwenglish.service.AccountDeletionService;
import com.njwenglish.service.AuthService;
import com.njwenglish.service.SignupService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    /** 쿠키 경로를 /api/auth로 좁힌다. 다른 API 요청에는 리프레시 토큰이 실려 나가지 않는다. */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final SignupService signupService;
    private final AccountDeletionService accountDeletionService;

    @Value("${app.jwt.refresh-token-validity-seconds}")
    private long refreshTokenValiditySeconds;

    /** 로컬 개발에서는 false. 운영에서는 반드시 true여야 한다. */
    @Value("${app.jwt.refresh-cookie-secure}")
    private boolean refreshCookieSecure;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletResponse response) {
        LoginResult result = authService.login(request);
        setRefreshCookie(response, result.refreshToken(), refreshTokenValiditySeconds);
        return ApiResponse.ok(result.body());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        return ApiResponse.ok(new TokenResponse(authService.refresh(refreshToken)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
        HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        expireRefreshCookie(response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    /** 리프레시 토큰이 전부 폐기되므로 쿠키도 함께 지운다. 프론트는 다시 로그인시킨다. */
    @PatchMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request,
                                            HttpServletResponse response) {
        authService.changePassword(request);
        expireRefreshCookie(response);
        return ApiResponse.ok();
    }

    /**
     * 본인 계정 삭제(학생·학부모). 로그인 계정만 지우고 학습 기록은 남긴다 — AccountDeletionService.
     * DELETE에 본문을 싣지 않으려고 POST다(프록시가 DELETE 본문을 버리는 경우가 있다).
     */
    @PostMapping("/account/delete")
    public ApiResponse<Void> deleteAccount(@Valid @RequestBody AccountDeleteRequest request,
                                           HttpServletResponse response) {
        accountDeletionService.deleteMyAccount(request);
        expireRefreshCookie(response);
        return ApiResponse.ok();
    }

    /** 비로그인 호출. 반 코드·개인 코드 세 경로가 이 엔드포인트 하나를 쓴다. */
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(signupService.signup(request));
    }

    private void setRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(refreshCookieSecure)
            .sameSite("Lax")
            .path(REFRESH_COOKIE_PATH)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build().toString());
    }

    private void expireRefreshCookie(HttpServletResponse response) {
        setRefreshCookie(response, "", 0);
    }
}
