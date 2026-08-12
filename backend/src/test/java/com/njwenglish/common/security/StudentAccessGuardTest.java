package com.njwenglish.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.StudentRepository;
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

/**
 * 이후 모든 Phase의 회귀 방지용이다. 여기가 깨지면 학부모가 URL의 숫자만 바꿔
 * 남의 아이 성적을 본다.
 */
@ExtendWith(MockitoExtension.class)
class StudentAccessGuardTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentAccessGuard guard;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("선생님은 모든 학생에 접근할 수 있다")
    void 선생님은_모든_학생에_접근할_수_있다() {
        Student student = Fixtures.student(88L, "서동환");
        given(studentRepository.findById(88L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.teacher(1L));

        assertThat(guard.requireAccessible(88L)).isSameAs(student);
    }

    @Test
    @DisplayName("학생은 본인 데이터에 접근할 수 있다")
    void 학생은_본인_데이터에_접근할_수_있다() {
        User studentAccount = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
        Student student = Fixtures.student(88L, "서동환");
        student.linkUser(studentAccount);
        given(studentRepository.findById(88L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThat(guard.requireAccessible(88L)).isSameAs(student);
    }

    @Test
    @DisplayName("학생은 다른 학생의 데이터에 접근할 수 없다")
    void 학생은_다른_학생의_데이터에_접근할_수_없다() {
        Student other = Fixtures.student(92L, "서동희");
        other.linkUser(Fixtures.user(20L, UserRole.STUDENT, "01033334444"));
        given(studentRepository.findById(92L)).willReturn(Optional.of(other));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThatThrownBy(() -> guard.requireAccessible(92L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }

    @Test
    @DisplayName("학부모는 자기 자녀에 접근할 수 있다")
    void 학부모는_자기_자녀에_접근할_수_있다() {
        Parent parent = Fixtures.parent(5L, Fixtures.user(30L, UserRole.PARENT, "01098765432"));
        Student child = Fixtures.student(88L, "서동환");
        child.linkParent(parent);
        given(studentRepository.findById(88L)).willReturn(Optional.of(child));
        Fixtures.login(Fixtures.parentUser(30L));

        assertThat(guard.requireAccessible(88L)).isSameAs(child);
    }

    @Test
    @DisplayName("학부모는 다른 학부모의 자녀에 접근할 수 없다")
    void 학부모는_다른_학부모의_자녀에_접근할_수_없다() {
        Parent otherParent = Fixtures.parent(6L, Fixtures.user(31L, UserRole.PARENT, "01055556666"));
        Student otherChild = Fixtures.student(92L, "김민수");
        otherChild.linkParent(otherParent);
        given(studentRepository.findById(92L)).willReturn(Optional.of(otherChild));
        Fixtures.login(Fixtures.parentUser(30L));

        assertThatThrownBy(() -> guard.requireAccessible(92L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }

    @Test
    @DisplayName("학부모 연결이 없는 학생은 학부모가 접근할 수 없다")
    void 학부모_연결이_없는_학생은_학부모가_접근할_수_없다() {
        Student unlinked = Fixtures.student(93L, "이하늘");
        given(studentRepository.findById(93L)).willReturn(Optional.of(unlinked));
        Fixtures.login(Fixtures.parentUser(30L));

        assertThatThrownBy(() -> guard.requireAccessible(93L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }

    @Test
    @DisplayName("미가입 학생(user_id IS NULL)에 학생 역할로 접근해도 NPE가 나지 않는다")
    void 미가입_학생_접근시_NPE가_없다() {
        Student notSignedUp = Fixtures.student(94L, "박서준");
        given(studentRepository.findById(94L)).willReturn(Optional.of(notSignedUp));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThatThrownBy(() -> guard.requireAccessible(94L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }

    @Test
    @DisplayName("존재하지 않는 학생은 RESOURCE_NOT_FOUND다")
    void 존재하지_않는_학생은_RESOURCE_NOT_FOUND() {
        given(studentRepository.findById(999L)).willReturn(Optional.empty());
        Fixtures.login(Fixtures.teacher(1L));

        assertThatThrownBy(() -> guard.requireAccessible(999L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("인증 정보가 없으면 TOKEN_INVALID다")
    void 인증_정보가_없으면_TOKEN_INVALID() {
        assertThatThrownBy(() -> guard.requireAccessible(88L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("requireSelf는 로그인한 학생 본인의 Student를 반환한다")
    void requireSelf는_본인_Student를_반환한다() {
        Student student = Fixtures.student(88L, "서동환");
        given(studentRepository.findByUserId(10L)).willReturn(Optional.of(student));
        Fixtures.login(Fixtures.studentUser(10L));

        assertThat(guard.requireSelf()).isSameAs(student);
    }

    @Test
    @DisplayName("requireSelf는 연결된 학생이 없으면 STUDENT_NOT_ACCESSIBLE이다")
    void requireSelf는_연결이_없으면_거부한다() {
        given(studentRepository.findByUserId(10L)).willReturn(Optional.empty());
        Fixtures.login(Fixtures.studentUser(10L));

        assertThatThrownBy(() -> guard.requireSelf())
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }
}
