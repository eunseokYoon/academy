package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.dto.auth.AccountDeleteRequest;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import com.njwenglish.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 학생·학부모 본인의 계정 삭제(2026-10-03). 로그인 계정만 지우고 학습 기록은 남긴다. */
@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private ParentRepository parentRepository;
    @Mock private TokenService tokenService;
    @Mock private PasswordEncoder passwordEncoder;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(userRepository, studentRepository, parentRepository,
            tokenService, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User loginAs(User user, String role) {
        Fixtures.login(role.equals("STUDENT")
            ? Fixtures.studentUser(user.getId()) : Fixtures.parentUser(user.getId()));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw-1234", user.getPasswordHash())).willReturn(true);
        return user;
    }

    @Test
    @DisplayName("학생이 지우면 계정과 토큰만 사라지고 학생 행은 미가입 학생으로 남는다")
    void studentKeepsRecords() {
        User user = loginAs(Fixtures.user(10L, UserRole.STUDENT, "01011112222", "hash"),
            "STUDENT");
        Student me = Fixtures.student(88L, "김하늘");
        me.linkUser(user);
        given(studentRepository.findByUserId(10L)).willReturn(Optional.of(me));

        service.deleteMyAccount(new AccountDeleteRequest("pw-1234"));

        assertThat(me.getUser()).isNull();
        verify(studentRepository, never()).delete(any());
        InOrder order = inOrder(tokenService, userRepository);
        order.verify(tokenService).deleteAllOf(10L);
        order.verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("학부모가 지우면 학부모 행이 사라지고 자녀는 연결만 풀린다")
    void parentUnlinksChildren() {
        User user = loginAs(Fixtures.user(20L, UserRole.PARENT, "01033334444", "hash"),
            "PARENT");
        Parent parent = Fixtures.parent(5L, user);
        Student child = Fixtures.student(88L, "김하늘");
        child.linkParent(parent);
        User childUser = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
        child.linkUser(childUser);
        given(parentRepository.findByUserId(20L)).willReturn(Optional.of(parent));
        given(studentRepository.findByParentId(5L)).willReturn(List.of(child));

        service.deleteMyAccount(new AccountDeleteRequest("pw-1234"));

        assertThat(child.getParent()).isNull();
        assertThat(child.getUser()).isEqualTo(childUser); // 자녀 계정은 그대로
        verify(parentRepository).delete(parent);
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 아무것도 지우지 않는다")
    void wrongPasswordDeletesNothing() {
        User user = Fixtures.user(10L, UserRole.STUDENT, "01011112222", "hash");
        Fixtures.login(Fixtures.studentUser(10L));
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        assertThatThrownBy(() -> service.deleteMyAccount(new AccountDeleteRequest("wrong")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);
        verify(userRepository, never()).delete(any());
        verify(tokenService, never()).deleteAllOf(any());
    }

    @Test
    @DisplayName("선생님 계정은 지울 수 없다")
    void teacherCannotDelete() {
        User user = Fixtures.user(1L, UserRole.TEACHER, "01000000000", "hash");
        Fixtures.login(Fixtures.teacher(1L));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw-1234", "hash")).willReturn(true);

        assertThatThrownBy(() -> service.deleteMyAccount(new AccountDeleteRequest("pw-1234")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_ALLOWED);
        verify(userRepository, never()).delete(any());
    }
}
