package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.AuthUser;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.JwtTokenProvider;
import com.njwenglish.common.util.PhoneNumbers;
import com.njwenglish.dto.auth.LoginRequest;
import com.njwenglish.dto.auth.LoginResponse;
import com.njwenglish.dto.auth.LoginResult;
import com.njwenglish.dto.auth.MeResponse;
import com.njwenglish.dto.auth.PasswordChangeRequest;
import com.njwenglish.dto.auth.UserSummaryResponse;
import com.njwenglish.entity.User;
import com.njwenglish.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 비밀번호 정책은 길이뿐이다. 학부모 연령대를 고려해 특수문자 강제는 넣지 않는다. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
     * 구분하면 어떤 번호가 가입돼 있는지 알려주는 조회기가 된다.
     */
    @Transactional
    public LoginResult login(LoginRequest request) {
        String loginId = PhoneNumbers.normalize(request.loginId());
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return new LoginResult(
            new LoginResponse(accessTokenFor(user), UserSummaryResponse.from(user)),
            tokenService.issue(user));
    }

    @Transactional
    public String refresh(String refreshToken) {
        return accessTokenFor(tokenService.validate(refreshToken));
    }

    @Transactional
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        return MeResponse.from(currentUser());
    }

    /** 변경 성공 시 이 사용자의 리프레시 토큰을 전부 폐기한다. 다른 기기 세션을 끊는 게 맞다. */
    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        User user = currentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (request.newPassword() == null || request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        tokenService.revokeAllOf(user.getId());
    }

    private User currentUser() {
        AuthUser me = CurrentUser.get();
        return userRepository.findById(me.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
    }

    private String accessTokenFor(User user) {
        return jwtTokenProvider.createAccessToken(
            user.getId(), user.getRole(), user.isMustChangePassword());
    }
}
