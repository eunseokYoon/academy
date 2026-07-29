package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.JwtTokenProvider;
import com.njwenglish.dto.auth.LoginRequest;
import com.njwenglish.dto.auth.LoginResult;
import com.njwenglish.dto.auth.MeResponse;
import com.njwenglish.dto.auth.PasswordChangeRequest;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.UserRepository;
import com.njwenglish.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "academy-local-test-secret-key-academy-local-test-secret-key";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private User teacher;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, tokenService, passwordEncoder,
            new JwtTokenProvider(SECRET, 1800, 1209600));
        teacher = Fixtures.user(1L, UserRole.TEACHER, "01000000000", "교체된다");
        // 시드 선생님 계정은 must_change_password가 false다 (V2 시드는 DEFAULT를 쓴다)
        teacher.changePassword(passwordEncoder.encode("0000"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그인하면 액세스 토큰과 사용자 정보를 반환한다")
    void 로그인_성공() {
        given(userRepository.findByLoginId("01000000000")).willReturn(Optional.of(teacher));
        given(tokenService.issue(teacher)).willReturn("refresh-token");

        LoginResult result = authService.login(new LoginRequest("01000000000", "0000"));

        assertThat(result.body().accessToken()).isNotBlank();
        assertThat(result.body().user().id()).isEqualTo(1L);
        assertThat(result.body().user().role()).isEqualTo(UserRole.TEACHER);
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("하이픈을 넣어 입력해도 정규화되어 로그인된다")
    void 하이픈이_있어도_로그인된다() {
        given(userRepository.findByLoginId("01000000000")).willReturn(Optional.of(teacher));
        given(tokenService.issue(teacher)).willReturn("refresh-token");

        LoginResult result = authService.login(new LoginRequest("010-0000-0000", "0000"));

        assertThat(result.body().user().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("없는 아이디는 INVALID_CREDENTIALS다")
    void 없는_아이디는_INVALID_CREDENTIALS() {
        given(userRepository.findByLoginId("01099998888")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("01099998888", "0000")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 아이디 없음과 같은 INVALID_CREDENTIALS다")
    void 비밀번호가_틀려도_같은_오류다() {
        given(userRepository.findByLoginId("01000000000")).willReturn(Optional.of(teacher));

        assertThatThrownBy(() -> authService.login(new LoginRequest("01000000000", "wrong")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("me는 전화번호를 마스킹해 내려준다")
    void me는_번호를_마스킹한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(teacher));
        Fixtures.login(Fixtures.teacher(1L));

        MeResponse me = authService.me();

        assertThat(me.phone()).isEqualTo("010-****-0000");
        assertThat(me.role()).isEqualTo(UserRole.TEACHER);
        assertThat(me.mustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 mustChangePassword가 내려가고 리프레시 토큰이 전부 폐기된다")
    void 비밀번호_변경() {
        User student = Fixtures.user(10L, UserRole.STUDENT, "01011112222",
            passwordEncoder.encode("0000"));
        given(userRepository.findById(10L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.studentUser(10L));

        authService.changePassword(new PasswordChangeRequest("0000", "newpassword"));

        assertThat(student.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("newpassword", student.getPasswordHash())).isTrue();
        verify(tokenService).revokeAllOf(10L);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 변경되지 않는다")
    void 현재_비밀번호가_틀리면_거부한다() {
        User student = Fixtures.user(10L, UserRole.STUDENT, "01011112222",
            passwordEncoder.encode("0000"));
        given(userRepository.findById(10L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThatThrownBy(() ->
            authService.changePassword(new PasswordChangeRequest("9999", "newpassword")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        assertThat(student.isMustChangePassword()).isTrue();
        verify(tokenService, never()).revokeAllOf(any());
    }

    @Test
    @DisplayName("새 비밀번호가 8자 미만이면 VALIDATION_FAILED다")
    void 짧은_비밀번호는_거부한다() {
        User student = Fixtures.user(10L, UserRole.STUDENT, "01011112222",
            passwordEncoder.encode("0000"));
        given(userRepository.findById(10L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThatThrownBy(() ->
            authService.changePassword(new PasswordChangeRequest("0000", "short12")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("리프레시하면 새 액세스 토큰이 나온다")
    void 리프레시() {
        given(tokenService.validate("refresh-token")).willReturn(teacher);

        String accessToken = authService.refresh("refresh-token");

        assertThat(accessToken).isNotBlank();
    }

    @Test
    @DisplayName("로그아웃하면 해당 리프레시 토큰이 폐기된다")
    void 로그아웃() {
        authService.logout("refresh-token");

        verify(tokenService).revoke("refresh-token");
    }
}
