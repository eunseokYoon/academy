package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.AuthUser;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.dto.push.DeviceTokenRequest;
import com.njwenglish.dto.push.PushSettingResponse;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.DeviceTokenRepository;
import com.njwenglish.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱의 기기 토큰과 알림 끄기.
 *
 * <p><b>선생님은 등록할 수 없다</b>(알림을 받지 않는다, 확정). SecurityConfig 가 경로에서
 * 먼저 막지만 역할 검사를 여기에도 둔다 — 경로 규칙이 바뀌어도 TEACHER 행이 생기지 않게.
 */
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository tokenRepository;
    private final UserRepository userRepository;

    /** 로그인할 때·토큰이 바뀔 때마다 앱이 부른다. 같은 토큰이면 주인을 지금 사용자로 옮긴다. */
    @Transactional
    public void register(DeviceTokenRequest request) {
        AuthUser me = requireAppUser();
        tokenRepository.upsert(me.userId(), request.token(), request.platform().name());
    }

    /** 로그아웃 직전에 앱이 부른다. 없는 토큰이어도 200 이다(이미 정리됐다). */
    @Transactional
    public void unregister(String token) {
        AuthUser me = requireAppUser();
        tokenRepository.deleteMine(me.userId(), token);
    }

    @Transactional(readOnly = true)
    public PushSettingResponse setting() {
        return new PushSettingResponse(currentUser().isPushEnabled());
    }

    @Transactional
    public PushSettingResponse changeSetting(boolean enabled) {
        User user = currentUser();
        user.changePushEnabled(enabled);
        return new PushSettingResponse(user.isPushEnabled());
    }

    private User currentUser() {
        AuthUser me = requireAppUser();
        return userRepository.findById(me.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static AuthUser requireAppUser() {
        AuthUser me = CurrentUser.get();
        if (me.role() != UserRole.STUDENT && me.role() != UserRole.PARENT) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }
        return me;
    }
}
