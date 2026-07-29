package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.classroom.ClassRoomStudentsResponse;
import com.njwenglish.dto.classroom.EnrollmentCreateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private EnrollmentService enrollmentService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentRepository, classRoomRepository,
            studentAccessGuard);
    }

    @Test
    @DisplayName("미가입 학생도 이름과 함께 명단에 나온다")
    void 미가입_학생이_명단에_있다() {
        Student signedUp = Fixtures.student(88L, "서동환");
        User user = Fixtures.user(10L, UserRole.STUDENT, "01011112222");
        signedUp.linkUser(user);
        Student notSignedUp = Fixtures.student(91L, "김하늘");

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.findActiveEnrollments(3L, LocalDate.now())).willReturn(List.of(
            Enrollment.create(signedUp, classRoom, LocalDate.of(2026, 3, 2)),
            Enrollment.create(notSignedUp, classRoom, LocalDate.of(2026, 3, 2))));

        ClassRoomStudentsResponse response = enrollmentService.students(3L, null);

        assertThat(response.students())
            .extracting(ClassRoomStudentsResponse.Member::name,
                ClassRoomStudentsResponse.Member::signedUp)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("서동환", true),
                org.assertj.core.groups.Tuple.tuple("김하늘", false));
    }

    @Test
    @DisplayName("asOf를 주면 그 날짜 기준으로 조회한다")
    void 과거_시점_명단을_조회한다() {
        LocalDate asOf = LocalDate.of(2026, 5, 20);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.findActiveEnrollments(3L, asOf)).willReturn(List.of());

        enrollmentService.students(3L, asOf);

        verify(enrollmentRepository).findActiveEnrollments(3L, asOf);
    }

    @Test
    @DisplayName("이미 배정된 학생은 건너뛰고 나머지만 추가한다 (멱등)")
    void 중복_배정은_무시한다() {
        Student already = Fixtures.student(88L, "서동환");
        Student fresh = Fixtures.student(91L, "김하늘");
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(already);
        given(studentAccessGuard.requireAccessible(91L)).willReturn(fresh);
        given(enrollmentRepository.findByStudentIdAndClassRoomIdAndLeftAtIsNull(88L, 3L))
            .willReturn(Optional.of(Enrollment.create(already, classRoom, LocalDate.now())));
        given(enrollmentRepository.findByStudentIdAndClassRoomIdAndLeftAtIsNull(91L, 3L))
            .willReturn(Optional.empty());
        given(enrollmentRepository.findActiveEnrollments(any(), any())).willReturn(List.of());

        enrollmentService.assign(3L, new EnrollmentCreateRequest(
            List.of(88L, 91L), LocalDate.of(2026, 3, 2)));

        org.mockito.ArgumentCaptor<Enrollment> saved =
            org.mockito.ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(fresh);
        assertThat(saved.getValue().getJoinedAt()).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("배정 해제는 행을 지우지 않고 left_at을 기록한다")
    void 해제는_left_at을_기록한다() {
        Enrollment enrollment = Enrollment.create(
            Fixtures.student(88L, "서동환"), classRoom, LocalDate.of(2026, 3, 2));
        given(enrollmentRepository.findByStudentIdAndClassRoomIdAndLeftAtIsNull(88L, 3L))
            .willReturn(Optional.of(enrollment));

        enrollmentService.unassign(3L, 88L);

        assertThat(enrollment.getLeftAt()).isEqualTo(LocalDate.now());
        verify(enrollmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("배정되지 않은 학생을 해제하면 404다")
    void 없는_배정_해제는_404다() {
        given(enrollmentRepository.findByStudentIdAndClassRoomIdAndLeftAtIsNull(88L, 3L))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.unassign(3L, 88L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
