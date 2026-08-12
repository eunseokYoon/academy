package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.InviteCodeIssuer;
import com.njwenglish.dto.member.PasswordResetRequest;
import com.njwenglish.dto.member.PasswordResetResponse;
import com.njwenglish.dto.member.SignupCodeIssueRequest;
import com.njwenglish.dto.member.SignupCodeIssueResponse;
import com.njwenglish.dto.member.StudentCreateRequest;
import com.njwenglish.dto.member.StudentCreateResponse;
import com.njwenglish.dto.member.StudentDeleteResponse;
import com.njwenglish.dto.member.StudentWithdrawRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Parent;
import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.entity.enums.UserStatus;
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
class StudentServiceTest {

    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SignupCodeRepository signupCodeRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private ParentRepository parentRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentAccessGuard, studentRepository, userRepository,
            signupCodeRepository, enrollmentRepository, classRoomRepository,
            new InviteCodeIssuer(signupCodeRepository, classRoomRepository),
            tokenService, passwordEncoder,
            new ParentLinkService(userRepository, parentRepository, passwordEncoder),
            parentRepository);
    }

    private List<SignupCode> savedCodes() {
        ArgumentCaptor<SignupCode> captor = ArgumentCaptor.forClass(SignupCode.class);
        verify(signupCodeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("등록 (보조 경로)")
    class 등록 {

        private StudentCreateRequest request(List<Long> classRoomIds) {
            return new StudentCreateRequest("서동환", "010-1111-2222", "01098765432",
                "독해 보강", classRoomIds);
        }

        @Test
        @DisplayName("학생 코드는 1장만 만들고, 학부모는 코드 없이 계정이 바로 생긴다")
        void 학생코드와_학부모계정을_만든다() {
            given(studentRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(signupCodeRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(userRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(parentRepository.save(any())).willAnswer(i -> i.getArgument(0));

            StudentCreateResponse response = studentService.create(request(null));

            ArgumentCaptor<Student> student = ArgumentCaptor.forClass(Student.class);
            verify(studentRepository).save(student.capture());
            assertThat(student.getValue().getName()).isEqualTo("서동환");
            // 학생 계정은 여전히 없다. 학생은 코드로 직접 가입한다
            assertThat(student.getValue().getUser()).isNull();
            // 학부모는 이 시점에 이미 붙어 있다
            assertThat(student.getValue().getParent()).isNotNull();
            assertThat(student.getValue().getMemo()).isEqualTo("독해 보강");

            // 코드는 학생용 한 장뿐이다
            List<SignupCode> codes = savedCodes();
            assertThat(codes).hasSize(1);
            assertThat(codes.get(0).getTargetRole()).isEqualTo(UserRole.STUDENT);
            assertThat(codes.get(0).getPhone()).isEqualTo("01011112222");

            // 학부모 계정은 보호자 번호가 아이디이고 초기 비밀번호 상태다
            ArgumentCaptor<User> parentUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(parentUser.capture());
            assertThat(parentUser.getValue().getRole()).isEqualTo(UserRole.PARENT);
            assertThat(parentUser.getValue().getLoginId()).isEqualTo("01098765432");
            assertThat(parentUser.getValue().isMustChangePassword()).isTrue();
            assertThat(passwordEncoder.matches("0000", parentUser.getValue().getPasswordHash()))
                .isTrue();

            assertThat(response.signupCode().code()).matches("[A-Z2-9]{6}");
            assertThat(response.signupCode().expiresAt())
                .isAfter(OffsetDateTime.now().plusDays(6));
        }

        @Test
        @DisplayName("classRoomIds를 주면 enrollments가 함께 생긴다")
        void 반에_배정한다() {
            ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
            given(studentRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(signupCodeRepository.save(any())).willAnswer(i -> i.getArgument(0));
            given(classRoomRepository.findByIdIn(List.of(3L))).willReturn(List.of(classRoom));

            studentService.create(request(List.of(3L)));

            ArgumentCaptor<Enrollment> enrollment = ArgumentCaptor.forClass(Enrollment.class);
            verify(enrollmentRepository).save(enrollment.capture());
            assertThat(enrollment.getValue().getClassRoom()).isSameAs(classRoom);
            assertThat(enrollment.getValue().getJoinedAt()).isEqualTo(LocalDate.now());
        }

        @Test
        @DisplayName("학생 번호와 보호자 번호가 같으면 409다")
        void 같은_번호는_거부한다() {
            assertThatThrownBy(() -> studentService.create(new StudentCreateRequest(
                "서동환", "01011112222", "010-1111-2222", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);

            verify(studentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 계정이 있는 번호면 409다")
        void 쓰이는_번호는_거부한다() {
            given(userRepository.existsByLoginId("01011112222")).willReturn(true);

            assertThatThrownBy(() -> studentService.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }

        @Test
        @DisplayName("다른 학생에게 발급된 미사용 코드의 번호면 409다")
        void 선점된_번호는_거부한다() {
            given(signupCodeRepository.existsByPhoneAndUsedAtIsNull("01011112222"))
                .willReturn(true);

            assertThatThrownBy(() -> studentService.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @Nested
    @DisplayName("퇴원")
    class 퇴원 {

        private final LocalDate date = LocalDate.of(2026, 7, 31);

        @Test
        @DisplayName("미가입 학생도 오류 없이 처리되고 미사용 코드가 폐기된다")
        void 미가입_학생을_퇴원시킨다() {
            Student student = Fixtures.student(88L, "서동환");
            SignupCode pending = SignupCode.issue(student, UserRole.STUDENT, "K7F2QX",
                "01011112222", OffsetDateTime.now());
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(signupCodeRepository.findByStudentIdAndUsedAtIsNull(88L))
                .willReturn(List.of(pending));

            studentService.withdraw(88L, new StudentWithdrawRequest(date));

            assertThat(student.getStatus()).isEqualTo(StudentStatus.WITHDRAWN);
            assertThat(student.getWithdrawnAt()).isEqualTo(date);
            verify(signupCodeRepository).deleteAll(List.of(pending));
        }

        @Test
        @DisplayName("가입한 학생은 계정이 INACTIVE가 되고 리프레시 토큰이 폐기된다")
        void 로그인을_막는다() {
            Student student = Fixtures.student(88L, "서동환");
            User user = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
            student.linkUser(user);
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

            studentService.withdraw(88L, new StudentWithdrawRequest(date));

            assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(tokenService).revokeAllOf(10L);
        }

        @Test
        @DisplayName("진행 중인 배정에 left_at이 기록된다")
        void 배정을_종료한다() {
            Student student = Fixtures.student(88L, "서동환");
            Enrollment enrollment = Enrollment.create(student,
                Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q"), LocalDate.of(2026, 3, 2));
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(enrollmentRepository.findByStudentIdAndLeftAtIsNull(88L))
                .willReturn(List.of(enrollment));

            studentService.withdraw(88L, new StudentWithdrawRequest(date));

            assertThat(enrollment.getLeftAt()).isEqualTo(date);
        }

        @Test
        @DisplayName("다른 재원 자녀가 없으면 학부모 계정도 INACTIVE가 된다")
        void 마지막_자녀면_학부모도_막는다() {
            Student student = Fixtures.student(88L, "서동환");
            User parentUser = Fixtures.user(30L, UserRole.PARENT, "01098765432");
            student.linkParent(Fixtures.parent(5L, parentUser));
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(studentRepository.countByParentIdAndStatus(5L, StudentStatus.ENROLLED))
                .willReturn(0L);

            studentService.withdraw(88L, new StudentWithdrawRequest(date));

            assertThat(parentUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("형제가 남아 있으면 학부모 계정은 유지된다")
        void 자녀가_남으면_학부모는_유지한다() {
            Student student = Fixtures.student(88L, "서동환");
            User parentUser = Fixtures.user(30L, UserRole.PARENT, "01098765432");
            student.linkParent(Fixtures.parent(5L, parentUser));
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(studentRepository.countByParentIdAndStatus(5L, StudentStatus.ENROLLED))
                .willReturn(1L);

            studentService.withdraw(88L, new StudentWithdrawRequest(date));

            assertThat(parentUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("이미 재원 중인 학생을 복구하면 400이다")
        void 재원생_복구는_거부한다() {
            given(studentAccessGuard.requireAccessible(88L))
                .willReturn(Fixtures.student(88L, "서동환"));

            assertThatThrownBy(() -> studentService.restore(88L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("복구해도 반 배정은 되살리지 않는다")
        void 배정은_복구하지_않는다() {
            Student student = Fixtures.student(88L, "서동환");
            student.withdraw(date);
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

            assertThat(studentService.restore(88L).restoredEnrollments()).isZero();
            assertThat(student.getStatus()).isEqualTo(StudentStatus.ENROLLED);
            assertThat(student.getWithdrawnAt()).isNull();
            verify(enrollmentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("삭제 (반 코드로 들어온 제3자 전용)")
    class 삭제 {

        @Test
        @DisplayName("운영 기록이 있으면 409이고 아무것도 지우지 않는다")
        void 기록이_있으면_거부한다() {
            given(studentAccessGuard.requireAccessible(91L))
                .willReturn(Fixtures.student(91L, "제3자"));
            given(studentRepository.hasOperationalRecords(91L)).willReturn(true);

            assertThatThrownBy(() -> studentService.delete(91L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.STUDENT_HAS_RECORDS);

            verify(studentRepository, never()).delete(any());
        }

        @Test
        @DisplayName("유일한 자녀였으면 학부모 계정도 함께 지운다 — 자녀 없는 유령 계정을 남기지 않는다")
        void 단독_자녀면_학부모도_지운다() {
            Student student = Fixtures.student(91L, "제3자");
            User parentUser = Fixtures.user(30L, UserRole.PARENT, "01098765432");
            Parent parent = Fixtures.parent(5L, parentUser);
            student.linkParent(parent);
            given(studentAccessGuard.requireAccessible(91L)).willReturn(student);
            given(studentRepository.hasOperationalRecords(91L)).willReturn(false);
            given(studentRepository.countByParentId(5L)).willReturn(0L);

            studentService.delete(91L);

            verify(studentRepository).delete(student);
            verify(parentRepository).delete(parent);
            verify(userRepository).delete(parentUser);
            // revoke가 아니라 delete다. 행이 남으면 FK 때문에 users 삭제가 실패한다
            verify(tokenService).deleteAllOf(30L);
        }

        @Test
        @DisplayName("형제·자매가 남아 있으면 학부모 계정은 지우지 않는다")
        void 다른_자녀가_있으면_학부모를_남긴다() {
            Student student = Fixtures.student(91L, "제3자");
            User parentUser = Fixtures.user(30L, UserRole.PARENT, "01098765432");
            Parent parent = Fixtures.parent(5L, parentUser);
            student.linkParent(parent);
            given(studentAccessGuard.requireAccessible(91L)).willReturn(student);
            given(studentRepository.hasOperationalRecords(91L)).willReturn(false);
            given(studentRepository.countByParentId(5L)).willReturn(1L);

            studentService.delete(91L);

            verify(studentRepository).delete(student);
            verify(parentRepository, never()).delete(any());
            verify(userRepository, never()).delete(parentUser);
        }

        @Test
        @DisplayName("학부모가 붙어 있다는 것만으로는 막지 않는다 — 이제 전원 자동으로 붙는다")
        void 학부모_연결은_삭제를_막지_않는다() {
            Student student = Fixtures.student(91L, "제3자");
            student.linkParent(
                Fixtures.parent(5L, Fixtures.user(30L, UserRole.PARENT, "01098765432")));
            given(studentAccessGuard.requireAccessible(91L)).willReturn(student);
            given(studentRepository.hasOperationalRecords(91L)).willReturn(false);
            given(studentRepository.countByParentId(5L)).willReturn(0L);

            // 예전에는 여기서 409였다. 그때는 학부모가 코드로 직접 가입해야 붙었기 때문이다.
            studentService.delete(91L);

            verify(studentRepository).delete(student);
        }

        @Test
        @DisplayName("기록이 없으면 users·enrollments·signup_codes가 함께 정리된다")
        void 함께_정리한다() {
            Student student = Fixtures.student(91L, "제3자");
            User user = Fixtures.user(12L, UserRole.STUDENT, "01077778888");
            student.linkUser(user);
            given(studentAccessGuard.requireAccessible(91L)).willReturn(student);
            given(studentRepository.hasOperationalRecords(91L)).willReturn(false);
            given(enrollmentRepository.countByStudentId(91L)).willReturn(1L);

            StudentDeleteResponse response = studentService.delete(91L);

            assertThat(response.deletedStudentId()).isEqualTo(91L);
            assertThat(response.deletedUser()).isTrue();
            assertThat(response.deletedEnrollments()).isEqualTo(1L);

            // FK 순서: 자식 → students → users
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                studentRepository, enrollmentRepository, signupCodeRepository, userRepository);
            order.verify(studentRepository).deleteSubmissionPhotosOf(91L);
            order.verify(studentRepository).deleteSubmissionsOf(91L);
            order.verify(enrollmentRepository).deleteByStudentId(91L);
            order.verify(signupCodeRepository).deleteByStudentId(91L);
            order.verify(studentRepository).delete(student);
            order.verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("미가입 학생을 지우면 users 삭제는 건너뛴다")
        void 계정이_없으면_건너뛴다() {
            given(studentAccessGuard.requireAccessible(91L))
                .willReturn(Fixtures.student(91L, "제3자"));
            given(studentRepository.hasOperationalRecords(91L)).willReturn(false);

            assertThat(studentService.delete(91L).deletedUser()).isFalse();
            verify(userRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("코드 재발급")
    class 코드_재발급 {

        @Test
        @DisplayName("이전 미사용 코드를 폐기하고 새로 발급한다")
        void 이전_코드를_폐기한다() {
            Student student = Fixtures.student(88L, "서동환");
            SignupCode old = SignupCode.issue(student, UserRole.STUDENT, "K7F2QX",
                "01011112222", OffsetDateTime.now());
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(signupCodeRepository
                .findByStudentIdAndTargetRoleAndUsedAtIsNull(88L, UserRole.STUDENT))
                .willReturn(List.of(old));
            given(signupCodeRepository.save(any())).willAnswer(i -> i.getArgument(0));

            SignupCodeIssueResponse response = studentService.issueSignupCode(
                88L, new SignupCodeIssueRequest(UserRole.STUDENT, null));

            verify(signupCodeRepository).deleteAll(List.of(old));
            // phone을 생략하면 기존 번호를 유지한다
            assertThat(response.phone()).isEqualTo("01011112222");
            assertThat(response.code()).matches("[A-Z2-9]{6}").isNotEqualTo("K7F2QX");
        }

        @Test
        @DisplayName("이미 가입을 마친 대상이면 400이다 — 그때 필요한 건 reset-password다")
        void 가입한_대상은_거부한다() {
            Student student = Fixtures.student(88L, "서동환");
            student.linkUser(Fixtures.user(10L, UserRole.STUDENT, "01011112222"));
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

            assertThatThrownBy(() -> studentService.issueSignupCode(
                88L, new SignupCodeIssueRequest(UserRole.STUDENT, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("phone을 함께 주면 대조 번호도 바뀐다")
        void 번호도_바꾼다() {
            Student student = Fixtures.student(88L, "서동환");
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
            given(signupCodeRepository
                .findByStudentIdAndTargetRoleAndUsedAtIsNull(88L, UserRole.STUDENT))
                .willReturn(List.of());
            given(signupCodeRepository.save(any())).willAnswer(i -> i.getArgument(0));

            SignupCodeIssueResponse response = studentService.issueSignupCode(
                88L, new SignupCodeIssueRequest(UserRole.STUDENT, "010-3333-4444"));

            assertThat(response.phone()).isEqualTo("01033334444");
            assertThat(response.target()).isEqualTo(UserRole.STUDENT);
        }

        @Test
        @DisplayName("target=PARENT는 400이다 — 학부모 코드는 발급하지 않는다")
        void 학부모_코드는_발급하지_않는다() {
            given(studentAccessGuard.requireAccessible(88L))
                .willReturn(Fixtures.student(88L, "서동환"));

            assertThatThrownBy(() -> studentService.issueSignupCode(
                88L, new SignupCodeIssueRequest(UserRole.PARENT, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

            verify(signupCodeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("비밀번호 초기화")
    class 비밀번호_초기화 {

        @Test
        @DisplayName("임시 비밀번호가 만들어지고 변경 강제 상태가 되며 토큰이 폐기된다")
        void 초기화한다() {
            Student student = Fixtures.student(88L, "서동환");
            User user = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
            user.changePassword(passwordEncoder.encode("myOwnPassword"));
            student.linkUser(user);
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

            PasswordResetResponse response = studentService.resetPassword(
                88L, new PasswordResetRequest(null, null));

            assertThat(response.target()).isEqualTo(UserRole.STUDENT);
            assertThat(response.loginId()).isEqualTo("01011112222");
            assertThat(response.temporaryPassword()).matches("[A-Z2-9]{8}");
            assertThat(passwordEncoder.matches(response.temporaryPassword(),
                user.getPasswordHash())).isTrue();
            assertThat(user.isMustChangePassword()).isTrue();
            verify(tokenService).revokeAllOf(10L);
        }

        @Test
        @DisplayName("아직 가입하지 않은 대상이면 400이다")
        void 미가입이면_거부한다() {
            given(studentAccessGuard.requireAccessible(88L))
                .willReturn(Fixtures.student(88L, "서동환"));

            assertThatThrownBy(() -> studentService.resetPassword(
                88L, new PasswordResetRequest(UserRole.STUDENT, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        @Test
        @DisplayName("target=PARENT면 연결된 학부모 계정을 초기화한다")
        void 학부모_계정을_초기화한다() {
            Student student = Fixtures.student(88L, "서동환");
            User parentUser = Fixtures.user(30L, UserRole.PARENT, "01098765432");
            Parent parent = Fixtures.parent(5L, parentUser);
            student.linkParent(parent);
            given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

            PasswordResetResponse response = studentService.resetPassword(
                88L, new PasswordResetRequest(UserRole.PARENT, "temp1234"));

            assertThat(response.loginId()).isEqualTo("01098765432");
            assertThat(response.temporaryPassword()).isEqualTo("temp1234");
            assertThat(passwordEncoder.matches("temp1234", parentUser.getPasswordHash())).isTrue();
            verify(tokenService).revokeAllOf(30L);
        }
    }

    @Test
    @DisplayName("목록 정렬은 sort=recent면 created_at DESC, 기본은 이름순이다")
    void 정렬_기준() {
        given(studentRepository.search(any(), any(), any(), any(), any()))
            .willReturn(org.springframework.data.domain.Page.empty());

        studentService.list(null, null, null, "recent", 0, 20);
        studentService.list(null, null, null, null, 0, 20);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
            ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(studentRepository, org.mockito.Mockito.times(2))
            .search(any(), any(), any(), any(), pageable.capture());

        assertThat(pageable.getAllValues().get(0).getSort().toString())
            .isEqualTo("createdAt: DESC");
        assertThat(pageable.getAllValues().get(1).getSort().toString())
            .isEqualTo("name: ASC");
    }

    @Test
    @DisplayName("검색어의 하이픈은 번호 검색에서 무시된다")
    void 번호_검색은_숫자만_본다() {
        given(studentRepository.search(any(), any(), any(), any(), any()))
            .willReturn(org.springframework.data.domain.Page.empty());

        studentService.list(null, null, "010-1111", null, 0, 20);

        verify(studentRepository).search(null, null, "%010-1111%", "%0101111%",
            org.springframework.data.domain.PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.ASC, "name")));
    }

    @Test
    @DisplayName("빈 목록이어도 부가 조회를 하지 않는다")
    void 빈_목록은_추가조회하지_않는다() {
        given(studentRepository.search(any(), any(), any(), any(), any()))
            .willReturn(org.springframework.data.domain.Page.empty());

        studentService.list(null, null, null, null, 0, 20);

        verify(enrollmentRepository, never()).findActiveByStudentIds(anyList());
        verify(signupCodeRepository, never()).findByStudentIdInAndUsedAtIsNull(anyList());
    }
}
