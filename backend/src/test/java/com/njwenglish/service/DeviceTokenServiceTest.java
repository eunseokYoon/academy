package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.dto.push.DeviceTokenRequest;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.DevicePlatform;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.DeviceTokenRepository;
import com.njwenglish.repository.UserRepository;
import com.njwenglish.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private DeviceTokenService service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("선생님은 기기를 등록할 수 없다 — 선생님은 알림을 받지 않는다")
    void 선생님은_등록_불가() {
        Fixtures.login(Fixtures.teacher(1L));

        assertThatThrownBy(() -> service.register(
            new DeviceTokenRequest("tok", DevicePlatform.ANDROID)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ROLE_NOT_ALLOWED);
        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("학부모 등록은 지금 사용자로 UPSERT 한다")
    void 등록은_upsert() {
        Fixtures.login(Fixtures.parentUser(20L));

        service.register(new DeviceTokenRequest("tok", DevicePlatform.IOS));

        verify(tokenRepository).upsert(20L, "tok", "IOS");
    }

    @Test
    @DisplayName("해제는 내 토큰만 지운다")
    void 해제는_내_것만() {
        Fixtures.login(Fixtures.studentUser(10L));

        service.unregister("tok");

        verify(tokenRepository).deleteMine(10L, "tok");
    }

    @Test
    @DisplayName("알림 끄기는 users 의 한 칸이다")
    void 알림_끄기() {
        Fixtures.login(Fixtures.studentUser(10L));
        User user = Fixtures.user(10L, UserRole.STUDENT, "01012345678");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));

        assertThat(service.setting().enabled()).isTrue();
        assertThat(service.changeSetting(false).enabled()).isFalse();
        assertThat(user.isPushEnabled()).isFalse();
    }
}
