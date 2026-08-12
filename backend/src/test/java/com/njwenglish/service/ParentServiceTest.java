package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.dto.member.ParentMeResponse;
import com.njwenglish.dto.member.ParentPhoneUpdateRequest;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.StudentStatus;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ParentServiceTest {

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ParentService parentService;

    private User parentAccount;
    private Parent parent;

    @BeforeEach
    void setUp() {
        parentAccount = Fixtures.user(30L, UserRole.PARENT, "01098765432");
        parent = Fixtures.parent(5L, parentAccount);
        Fixtures.login(Fixtures.parentUser(30L));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void twoChildren() {
        Student first = Fixtures.student(88L, "서동환");
        Student second = Fixtures.student(92L, "서동희");
        given(parentRepository.findByUserId(30L)).willReturn(Optional.of(parent));
        given(studentRepository.findByParentIdAndStatusOrderByNameAsc(5L, StudentStatus.ENROLLED))
            .willReturn(List.of(first, second));
    }

    @Test
    @DisplayName("재원 중인 자녀만 students.name으로 내려준다")
    void 자녀_목록() {
        twoChildren();

        List<ChildResponse> children = parentService.children();

        assertThat(children).extracting(ChildResponse::studentId).containsExactly(88L, 92L);
        assertThat(children).extracting(ChildResponse::name).containsExactly("서동환", "서동희");
    }

    @Test
    @DisplayName("내 정보에 연락처와 자녀 목록이 함께 담긴다")
    void 내_정보() {
        twoChildren();

        ParentMeResponse me = parentService.me();

        assertThat(me.phone()).isEqualTo("01098765432");
        assertThat(me.children()).hasSize(2);
    }

    @Test
    @DisplayName("번호를 바꾸면 login_id도 함께 바뀐다")
    void 번호_변경() {
        given(userRepository.findById(30L)).willReturn(Optional.of(parentAccount));
        given(userRepository.existsByLoginId("01055556666")).willReturn(false);
        given(parentRepository.findByUserId(30L)).willReturn(Optional.of(parent));
        given(studentRepository.findByParentIdAndStatusOrderByNameAsc(5L, StudentStatus.ENROLLED))
            .willReturn(List.of());

        parentService.changePhone(new ParentPhoneUpdateRequest("010-5555-6666"));

        assertThat(parentAccount.getPhone()).isEqualTo("01055556666");
        assertThat(parentAccount.getLoginId()).isEqualTo("01055556666");
    }

    @Test
    @DisplayName("이미 다른 계정이 쓰는 번호면 409다")
    void 중복_번호는_거부한다() {
        given(userRepository.findById(30L)).willReturn(Optional.of(parentAccount));
        given(userRepository.existsByLoginId("01011112222")).willReturn(true);

        assertThatThrownBy(() ->
            parentService.changePhone(new ParentPhoneUpdateRequest("01011112222")))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);

        assertThat(parentAccount.getPhone()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("쓰던 번호를 그대로 저장해도 중복 오류가 나지 않는다")
    void 같은_번호_저장은_허용한다() {
        given(userRepository.findById(30L)).willReturn(Optional.of(parentAccount));
        given(parentRepository.findByUserId(30L)).willReturn(Optional.of(parent));
        given(studentRepository.findByParentIdAndStatusOrderByNameAsc(5L, StudentStatus.ENROLLED))
            .willReturn(List.of());

        parentService.changePhone(new ParentPhoneUpdateRequest("010-9876-5432"));

        assertThat(parentAccount.getLoginId()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("학부모 계정이 없으면 RESOURCE_NOT_FOUND다")
    void 학부모_행이_없으면_404() {
        given(parentRepository.findByUserId(30L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> parentService.children())
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
