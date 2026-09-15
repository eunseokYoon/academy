package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.home.ParentHomeResponse;
import com.njwenglish.dto.home.StudentHomeResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.entity.enums.UserRole;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 홈은 값이 없을 때 <b>null을 내려보내야</b> 하는 화면이다.
 * 0이나 임의 값을 채우면 "시험이 오늘"이나 "출석 0회"로 읽힌다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeServiceTest {

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ClinicReservationRepository clinicReservationRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ExamScheduleService examScheduleService;
    @Mock
    private NoticeService noticeService;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Student me = Fixtures.student(88L, "서동환");

    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(lessonRepository, submissionRepository, attendanceRepository,
            clinicReservationRepository, enrollmentRepository, examScheduleService, noticeService,
            studentAccessGuard);

        given(examScheduleService.findNextExam(any())).willReturn(Optional.empty());
        given(lessonRepository.findNextForStudent(any(), any())).willReturn(Optional.empty());
        given(clinicReservationRepository.findNextReserved(any(), any()))
            .willReturn(Optional.empty());
        given(attendanceRepository.findConfirmedStatuses(any(), any(), any()))
            .willReturn(List.of());
        given(enrollmentRepository.findByStudentIdAndLeftAtIsNull(any())).willReturn(List.of());
        given(submissionRepository.findOpenByStudent(any())).willReturn(List.of());
    }

    private Submission overdue(Long id, OffsetDateTime dueAt) {
        return Fixtures.submission(id, Fixtures.homework(720L, classRoom, dueAt), me);
    }

    // ---------- S-1 ----------

    @Test
    @DisplayName("마감이 지난 미제출 숙제도 홈에 남는다 — 사라지면 학생이 잊는다")
    void 마감_지난_미제출도_홈에_보인다() {
        OffsetDateTime past = OffsetDateTime.now().minusDays(3);
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(submissionRepository.findOpenByStudent(88L))
            .willReturn(List.of(overdue(1001L, past)));

        StudentHomeResponse home = homeService.studentHome();

        assertThat(home.currentHomeworks()).hasSize(1);
        assertThat(home.currentHomeworks().get(0).status())
            .isEqualTo(SubmissionStatus.NOT_SUBMITTED);
        // 음수면 마감이 지난 것이다. 그래도 목록에서 빼지 않는다
        assertThat(home.currentHomeworks().get(0).remainingMinutes()).isNegative();
    }

    @Test
    @DisplayName("미제출 조회에 기간 하한이 없다 — 오래 밀린 숙제도 홈에 남는다")
    void 미제출_조회에_기간_하한을_두지_않는다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);

        homeService.studentHome();

        verify(submissionRepository).findOpenByStudent(88L);
    }

    @Test
    @DisplayName("수업·시험이 없으면 각각 null이다 — 0을 채우지 않는다")
    void 없는_값은_null이다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);

        StudentHomeResponse home = homeService.studentHome();

        assertThat(home.nextLesson()).isNull();
        assertThat(home.nextExam()).isNull();
        assertThat(home.student().name()).isEqualTo("서동환");
    }

    @Test
    @DisplayName("다음 수업이 있으면 D-day와 반 이름이 함께 나온다")
    void 다음_수업은_D_day를_계산한다() {
        LocalDate target = LocalDate.now().plusDays(5);
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(lessonRepository.findNextForStudent(eq(88L), any()))
            .willReturn(Optional.of(Fixtures.lesson(501L, classRoom, target)));

        StudentHomeResponse home = homeService.studentHome();

        assertThat(home.nextLesson().dDay()).isEqualTo(5);
        assertThat(home.nextLesson().classRoomName()).isEqualTo("고2 심화반");
    }

    @Test
    @DisplayName("학생 홈도 다음 클리닉을 내려준다 — 학부모 홈과 같은 조회다")
    void 학생_홈에_다음_클리닉이_있다() {
        LocalDate clinicDate = LocalDate.now().plusDays(5);
        // Fixtures.clinic의 종료 시각은 시작+1시간이다. 여기서는 상관없다 —
        // NextClinicResponse.from은 클리닉의 시간대가 아니라 예약 행의 도착 시각을 읽는다
        Clinic clinic = Fixtures.clinic(9L, clinicDate, LocalTime.of(17, 0), (short) 6);
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(clinicReservationRepository.findNextReserved(eq(88L), any()))
            .willReturn(Optional.of(Fixtures.reservationAt(
                40L, clinic, me, Fixtures.teacherEntity(1L), LocalTime.of(19, 0))));

        StudentHomeResponse home = homeService.studentHome();

        assertThat(home.nextClinic()).isNotNull();
        assertThat(home.nextClinic().clinicDate()).isEqualTo(clinicDate);
        // 시간대 시작(17:00)이 아니라 이 학생의 도착 시각이다.
        // 시간대 시작을 내려주면 22시에 가기로 한 학생을 학부모가 17시에 보낸다
        assertThat(home.nextClinic().arrivalTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(home.nextClinic().dDay()).isEqualTo(5);
    }

    @Test
    @DisplayName("배정된 클리닉이 없으면 학생 홈의 nextClinic도 null이다")
    void 학생_홈_클리닉이_없으면_null이다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);

        assertThat(homeService.studentHome().nextClinic()).isNull();
    }

    // ---------- P-1 ----------

    @Test
    @DisplayName("학부모 홈의 첫 줄은 requireAccessible이다 — 남의 자녀면 403이다")
    void 남의_자녀_홈은_403이다() {
        given(studentAccessGuard.requireAccessible(99L))
            .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));

        assertThatThrownBy(() -> homeService.parentHome(99L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDENT_NOT_ACCESSIBLE);

        verify(lessonRepository, never()).findNextForStudent(any(), any());
    }

    @Test
    @DisplayName("자녀 전화번호는 원본으로 내려준다 — 보호자가 자기 자녀 번호를 보는 것이다")
    void 자녀_전화번호는_원본이다() {
        User user = Fixtures.user(500L, UserRole.STUDENT, "01011112222");
        ReflectionTestUtils.setField(me, "user", user);
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);

        ParentHomeResponse home = homeService.parentHome(88L);

        assertThat(home.student().phone()).isEqualTo("01011112222");
    }

    @Test
    @DisplayName("미가입 자녀는 users 행이 없어 전화번호가 null이다")
    void 미가입_자녀는_전화번호가_null이다() {
        Student unsigned = Fixtures.student(92L, "서동희");
        given(studentAccessGuard.requireAccessible(92L)).willReturn(unsigned);

        ParentHomeResponse home = homeService.parentHome(92L);

        assertThat(home.student().name()).isEqualTo("서동희");
        assertThat(home.student().phone()).isNull();
    }

    @Test
    @DisplayName("학부모 홈은 다음 수업 날짜만 내려준다 — 제목·내용은 노출하지 않는다")
    void 학부모에게는_수업_날짜만_준다() {
        LocalDate target = LocalDate.now().plusDays(4);
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);
        given(lessonRepository.findNextForStudent(eq(88L), any()))
            .willReturn(Optional.of(Fixtures.lesson(501L, classRoom, target)));

        ParentHomeResponse home = homeService.parentHome(88L);

        assertThat(home.nextLessonDate()).isEqualTo(target);
    }

    /** 프론트가 날짜로 세면 기기 시계에 따라 학생 화면과 하루 어긋난다. 서버가 센다. */
    @Test
    @DisplayName("학부모 홈이 다음 수업 D-day를 내려준다")
    void 학부모_홈에_수업_D_day가_있다() {
        LocalDate target = LocalDate.now().plusDays(2);
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);
        given(lessonRepository.findNextForStudent(eq(88L), any()))
            .willReturn(Optional.of(Fixtures.lesson(501L, classRoom, target)));

        ParentHomeResponse home = homeService.parentHome(88L);

        assertThat(home.nextLessonDDay()).isEqualTo(2);
    }

    @Test
    @DisplayName("다음 수업이 없으면 D-day는 null이다")
    void 수업이_없으면_D_day는_null이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);

        assertThat(homeService.parentHome(88L).nextLessonDDay()).isNull();
    }

    @Test
    @DisplayName("클리닉 예약이 없으면 nextClinic은 null이다")
    void 클리닉이_없으면_null이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);

        assertThat(homeService.parentHome(88L).nextClinic()).isNull();
    }

    @Test
    @DisplayName("안 낸 숙제 수는 재제출 대상 기준이다 — ⭕를 받은 GRID 칸은 세지 않는다")
    void 안_낸_숙제_수는_재제출_대상_기준이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(me);
        given(submissionRepository.countPendingHomeworks(88L)).willReturn(2L);

        ParentHomeResponse home = homeService.parentHome(88L);

        assertThat(home.pendingHomeworkCount()).isEqualTo(2L);
        verify(submissionRepository).countPendingHomeworks(88L);
    }
}
