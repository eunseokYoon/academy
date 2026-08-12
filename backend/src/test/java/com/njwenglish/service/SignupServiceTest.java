package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.dto.auth.SignupRequest;
import com.njwenglish.dto.auth.SignupResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.ParentRepository;
import com.njwenglish.repository.SignupCodeRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.UserRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private SignupCodeRepository signupCodeRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ParentRepository parentRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private SignupService signupService;

    @BeforeEach
    void setUp() {
        ParentLinkService parentLinkService = new ParentLinkService(
            userRepository, parentRepository, passwordEncoder);
        signupService = new SignupService(signupCodeRepository, classRoomRepository,
            userRepository, studentRepository, enrollmentRepository, parentLinkService,
            passwordEncoder);
    }

    private void echoSaves() {
        given(studentRepository.save(any())).willAnswer(i -> i.getArgument(0));
        given(userRepository.save(any())).willAnswer(i -> i.getArgument(0));
        // 반 코드 가입은 학생·학부모 두 계정을 만든다. 실패 케이스에서는 안 불려서 lenient다
        lenient().when(parentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * 반 코드 가입은 users를 두 번 저장한다(학생·학부모). 역할로 골라야
     * "학생 계정" 검증에 학부모 계정이 섞이지 않는다.
     */
    private User savedUserOf(UserRole role) {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
            .filter(user -> user.getRole() == role)
            .findFirst()
            .orElseThrow(() -> new AssertionError(role + " 계정이 저장되지 않았다"));
    }

    @Nested
    @DisplayName("반 코드 (학생 자가 가입, 주 경로)")
    class 반_코드 {

        private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");

        private void codeFound() {
            given(signupCodeRepository.findByCode("HK7F2Q")).willReturn(Optional.empty());
            given(classRoomRepository.findByJoinCode("HK7F2Q")).willReturn(Optional.of(classRoom));
        }

        private SignupRequest request() {
            return new SignupRequest("HK7F2Q", "서동환", "01011112222", "01098765432");
        }

        @Test
        @DisplayName("students + users + enrollments가 한 번에 생성된다")
        void 세_행이_한번에_생긴다() {
            codeFound();
            echoSaves();

            SignupResponse response = signupService.signup(request());

            ArgumentCaptor<Student> student = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).save(student.capture());
            assertThat(student.getValue().getName()).isEqualTo("서동환");
            assertThat(student.getValue().getUser()).isNotNull();

            ArgumentCaptor<Enrollment> enrollment = ArgumentCaptor.forClass(Enrollment.class);
            verify(enrollmentRepository).save(enrollment.capture());
            assertThat(enrollment.getValue().getClassRoom()).isSameAs(classRoom);
            assertThat(enrollment.getValue().getJoinedAt()).isEqualTo(LocalDate.now());

            assertThat(response.role()).isEqualTo(UserRole.STUDENT);
            assertThat(response.loginId()).isEqualTo("01011112222");
            assertThat(response.studentName()).isEqualTo("서동환");
            assertThat(response.classRoomName()).isEqualTo("고2 심화반");
            assertThat(response.initialPassword()).isEqualTo("0000");
        }

        @Test
        @DisplayName("계정은 초기 비밀번호 0000에 변경 강제 상태로 만들어진다")
        void 초기_비밀번호는_0000이다() {
            codeFound();
            echoSaves();

            signupService.signup(request());

            User created = savedUserOf(UserRole.STUDENT);
            assertThat(created.getLoginId()).isEqualTo("01011112222");
            assertThat(created.isMustChangePassword()).isTrue();
            assertThat(passwordEncoder.matches("0000", created.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("학부모 계정이 보호자 번호로 바로 만들어진다 — 코드는 발급하지 않는다")
        void 학부모_계정이_바로_생긴다() {
            codeFound();
            echoSaves();

            signupService.signup(request());

            User parent = savedUserOf(UserRole.PARENT);
            assertThat(parent.getLoginId()).isEqualTo("01098765432");
            assertThat(parent.getName()).isEqualTo("서동환 학부모");
            assertThat(parent.isMustChangePassword()).isTrue();
            assertThat(passwordEncoder.matches("0000", parent.getPasswordHash())).isTrue();

            ArgumentCaptor<Student> student = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).save(student.capture());
            assertThat(student.getValue().getParent()).isNotNull();

            // 학부모 가입 코드는 더 이상 없다
            verify(signupCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("하이픈이 섞여 들어와도 정규화되어 저장된다")
        void 번호를_정규화한다() {
            codeFound();
            echoSaves();

            SignupResponse response = signupService.signup(
                new SignupRequest("HK7F2Q", "서동환", "010-1111-2222", "010-9876-5432"));

            assertThat(response.loginId()).isEqualTo("01011112222");
            assertThat(savedUserOf(UserRole.PARENT).getLoginId()).isEqualTo("01098765432");
        }

        @Test
        @DisplayName("보호자 번호가 이미 학부모 계정이면 새로 만들지 않고 자녀만 붙인다 (형제·자매)")
        void 다자녀는_기존_학부모에_연결한다() {
            codeFound();
            echoSaves();
            User existing = Fixtures.user(30L, UserRole.PARENT, "01098765432", "x");
            existing.changePassword(passwordEncoder.encode("myOwnPassword"));
            Parent existingParent = Fixtures.parent(5L, existing);
            given(userRepository.findByLoginId("01098765432")).willReturn(Optional.of(existing));
            given(parentRepository.findByUserId(30L)).willReturn(Optional.of(existingParent));

            signupService.signup(request());

            ArgumentCaptor<Student> student = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).save(student.capture());
            assertThat(student.getValue().getParent()).isSameAs(existingParent);
            // 기존 계정의 비밀번호를 0000으로 되돌리면 이미 쓰던 학부모가 잠긴다
            assertThat(passwordEncoder.matches("myOwnPassword", existing.getPasswordHash()))
                .isTrue();
            assertThat(existing.isMustChangePassword()).isFalse();
            verify(parentRepository, never()).save(any());
        }

        @Test
        @DisplayName("보호자 번호가 이미 학생 계정이면 409다")
        void 보호자_번호가_학생계정이면_거부한다() {
            codeFound();
            echoSaves();
            given(userRepository.findByLoginId("01098765432"))
                .willReturn(Optional.of(Fixtures.user(10L, UserRole.STUDENT, "01098765432")));

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }

        @Test
        @DisplayName("소문자·공백으로 입력해도 같은 코드로 처리된다")
        void 코드를_대문자로_정규화한다() {
            codeFound();
            echoSaves();

            SignupResponse response = signupService.signup(
                new SignupRequest(" hk7f2q ", "서동환", "01011112222", "01098765432"));

            assertThat(response.classRoomName()).isEqualTo("고2 심화반");
        }

        @Test
        @DisplayName("join_code_active = false면 INVITE_CODE_INVALID다")
        void 닫힌_코드는_거부한다() {
            ClassRoom closedCode = Fixtures.classRoom(3L, "고2 심화반", "HK7F2Q",
                false, ClassRoomStatus.ACTIVE);
            given(signupCodeRepository.findByCode("HK7F2Q")).willReturn(Optional.empty());
            given(classRoomRepository.findByJoinCode("HK7F2Q")).willReturn(Optional.of(closedCode));

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("CLOSED 상태인 반의 코드는 INVITE_CODE_INVALID다")
        void 종료된_반은_거부한다() {
            ClassRoom closed = Fixtures.classRoom(3L, "고2 심화반", "HK7F2Q",
                true, ClassRoomStatus.CLOSED);
            given(signupCodeRepository.findByCode("HK7F2Q")).willReturn(Optional.empty());
            given(classRoomRepository.findByJoinCode("HK7F2Q")).willReturn(Optional.of(closed));

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("어느 테이블에도 없는 코드는 INVITE_CODE_INVALID다")
        void 없는_코드는_거부한다() {
            given(signupCodeRepository.findByCode("HK7F2Q")).willReturn(Optional.empty());
            given(classRoomRepository.findByJoinCode("HK7F2Q")).willReturn(Optional.empty());

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("학생 번호와 보호자 번호가 같으면 409다")
        void 같은_번호는_거부한다() {
            codeFound();

            assertThatThrownBy(() -> signupService.signup(
                new SignupRequest("HK7F2Q", "서동환", "01011112222", "010-1111-2222")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }

        @Test
        @DisplayName("이미 쓰이는 번호면 409다")
        void 중복_번호는_거부한다() {
            codeFound();
            given(userRepository.existsByLoginId("01011112222")).willReturn(true);

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }

        @Test
        @DisplayName("이름이 없으면 VALIDATION_FAILED다")
        void 이름이_없으면_거부한다() {
            codeFound();

            assertThatThrownBy(() -> signupService.signup(
                new SignupRequest("HK7F2Q", "  ", "01011112222", "01098765432")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("보호자 번호가 없으면 VALIDATION_FAILED다")
        void 보호자_번호가_없으면_거부한다() {
            codeFound();

            assertThatThrownBy(() -> signupService.signup(
                new SignupRequest("HK7F2Q", "서동환", "01011112222", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Nested
    @DisplayName("개인 코드 — 학생")
    class 학생_개인코드 {

        private final Student student = Fixtures.student(88L, "서동환");
        private final SignupCode code = SignupCode.issue(student, UserRole.STUDENT,
            "K7F2QX", "01011112222", OffsetDateTime.now());

        private SignupRequest request() {
            return new SignupRequest("K7F2QX", null, "01011112222", null);
        }

        @Test
        @DisplayName("계정이 만들어져 기존 students 행에 연결된다")
        void 계정을_붙인다() {
            given(signupCodeRepository.findByCode("K7F2QX")).willReturn(Optional.of(code));
            given(userRepository.save(any())).willAnswer(i -> i.getArgument(0));

            SignupResponse response = signupService.signup(request());

            assertThat(student.getUser()).isNotNull();
            assertThat(student.getUser().getRole()).isEqualTo(UserRole.STUDENT);
            assertThat(code.isUsed()).isTrue();
            assertThat(response.role()).isEqualTo(UserRole.STUDENT);
            assertThat(response.studentName()).isEqualTo("서동환");
            assertThat(response.classRoomName()).isNull();
            verify(studentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 사용된 코드는 INVITE_CODE_USED다")
        void 사용된_코드는_거부한다() {
            code.markUsed(OffsetDateTime.now());
            given(signupCodeRepository.findByCode("K7F2QX")).willReturn(Optional.of(code));

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_USED);
        }

        @Test
        @DisplayName("만료된 코드는 INVITE_CODE_INVALID다")
        void 만료된_코드는_거부한다() {
            SignupCode expired = SignupCode.issue(student, UserRole.STUDENT, "K7F2QX",
                "01011112222", OffsetDateTime.now().minusDays(SignupCode.VALID_DAYS + 1));
            given(signupCodeRepository.findByCode("K7F2QX")).willReturn(Optional.of(expired));

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("발급 시 등록된 번호와 다르면 코드 오류와 구분되지 않는다")
        void 번호가_다르면_같은_오류다() {
            given(signupCodeRepository.findByCode("K7F2QX")).willReturn(Optional.of(code));

            assertThatThrownBy(() -> signupService.signup(
                new SignupRequest("K7F2QX", null, "01099998888", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("이미 계정이 있는 번호면 409다")
        void 중복_번호는_거부한다() {
            given(signupCodeRepository.findByCode("K7F2QX")).willReturn(Optional.of(code));
            given(userRepository.existsByLoginId("01011112222")).willReturn(true);

            assertThatThrownBy(() -> signupService.signup(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

}
