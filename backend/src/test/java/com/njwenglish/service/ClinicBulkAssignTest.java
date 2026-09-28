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
import com.njwenglish.dto.clinic.ClinicBulkAssignRequest;
import com.njwenglish.dto.clinic.ClinicBulkAssignResponse;
import com.njwenglish.dto.clinic.ClinicBulkAssignResponse.Reason;
import com.njwenglish.dto.clinic.ClinicBulkAssignResponse.Skipped;
import com.njwenglish.dto.clinic.ClinicBulkUnassignRequest;
import com.njwenglish.dto.clinic.ClinicBulkUnassignResponse;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ClinicStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicChangeLogRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;

/** T-13 요일 일괄 배정·해제(2026-09-29). */
@ExtendWith(MockitoExtension.class)
class ClinicBulkAssignTest {

    private static final short TUESDAY = 2;

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ClinicRepository clinicRepository;
    @Mock
    private ClinicReservationRepository reservationRepository;
    @Mock
    private ClinicChangeLogRepository changeLogRepository;
    @Mock
    private NoticeService noticeService;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private ClinicReservationService service;

    private final Student seo = Fixtures.student(88L, "서동환");
    private final Student kim = Fixtures.student(91L, "김하늘");
    private final Teacher teacher = Fixtures.teacherEntity(1L);

    /** 오늘보다 뒤의 첫 화요일. */
    private final LocalDate tue = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
    /** 오늘보다 앞의 화요일. */
    private final LocalDate pastTue = LocalDate.now()
        .with(TemporalAdjusters.previous(DayOfWeek.TUESDAY));

    @BeforeEach
    void setUp() {
        service = new ClinicReservationService(clinicRepository, reservationRepository,
            changeLogRepository, noticeService, studentRepository, teacherRepository,
            studentAccessGuard, eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void givenTeacherAndStudents() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(studentRepository.findById(88L)).willReturn(Optional.of(seo));
        given(studentRepository.findById(91L)).willReturn(Optional.of(kim));
    }

    private void givenLocked(Clinic clinic, long reserved) {
        given(clinicRepository.findByIdForUpdate(clinic.getId())).willReturn(Optional.of(clinic));
        given(reservationRepository.countByClinicIdAndStatus(clinic.getId(),
            ReservationStatus.RESERVED)).willReturn(reserved);
    }

    @Test
    @DisplayName("그 요일의 열린 클리닉마다 넣고, 막힌 날짜는 이유와 함께 건너뛴다")
    void 막힌_날짜만_건너뛴다() {
        givenTeacherAndStudents();
        LocalDate to = tue.plusWeeks(3);
        // 같은 날 16:00 시간대가 먼저 있어도 17:00 을 품은 쪽에 넣는다
        Clinic early = Fixtures.clinic(10L, tue, LocalTime.of(16, 0), null);
        Clinic ok = Fixtures.clinic(11L, tue, LocalTime.of(17, 0), (short) 6);
        Clinic wednesday = Fixtures.clinic(12L, tue.plusDays(1), LocalTime.of(17, 0), null);
        Clinic evening = Fixtures.clinic(13L, tue.plusWeeks(2), LocalTime.of(19, 0), null);
        Clinic full = Fixtures.clinic(14L, tue.plusWeeks(3), LocalTime.of(17, 0), (short) 2);
        given(clinicRepository.findInRange(tue, to, ClinicStatus.OPEN))
            .willReturn(List.of(early, ok, wednesday, evening, full));
        givenLocked(ok, 0);
        givenLocked(full, 1); // 1 + 2 > 2
        given(reservationRepository.save(any(ClinicReservation.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        ClinicBulkAssignResponse response = service.bulkAssign(new ClinicBulkAssignRequest(
            TUESDAY, tue, to, LocalTime.of(17, 0), List.of(88L, 91L, 88L)));

        assertThat(response.clinics()).isEqualTo(1);
        assertThat(response.reservations()).isEqualTo(2); // 중복 id 는 한 번
        assertThat(response.skipped()).containsExactly(
            new Skipped(tue.plusWeeks(1), Reason.NO_CLINIC),
            new Skipped(tue.plusWeeks(2), Reason.NO_SLOT),
            new Skipped(tue.plusWeeks(3), Reason.FULL));
        // 다른 요일·다른 시간대는 건드리지 않는다
        verify(clinicRepository, never()).findByIdForUpdate(10L);
        verify(clinicRepository, never()).findByIdForUpdate(12L);
    }

    @Test
    @DisplayName("지난 날짜는 열린 클리닉이 있어도 넣지 않는다 — T-1 미확정에 쌓인다")
    void 지난_날짜는_건너뛴다() {
        givenTeacherAndStudents();
        Clinic past = Fixtures.clinic(20L, pastTue, LocalTime.of(17, 0), null);
        given(clinicRepository.findInRange(pastTue, pastTue, ClinicStatus.OPEN))
            .willReturn(List.of(past));

        ClinicBulkAssignResponse response = service.bulkAssign(new ClinicBulkAssignRequest(
            TUESDAY, pastTue, pastTue, null, List.of(88L, 91L)));

        assertThat(response.skipped()).containsExactly(new Skipped(pastTue, Reason.PAST));
        verify(clinicRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("이미 배정된 학생은 건너뛴다 — 여러 번 눌러도 안전하다")
    void 여러_번_눌러도_안전하다() {
        givenTeacherAndStudents();
        Clinic ok = Fixtures.clinic(11L, tue, LocalTime.of(17, 0), null);
        given(clinicRepository.findInRange(tue, tue, ClinicStatus.OPEN)).willReturn(List.of(ok));
        given(clinicRepository.findByIdForUpdate(11L)).willReturn(Optional.of(ok));
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            11L, 88L, ReservationStatus.RESERVED)).willReturn(true);
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            11L, 91L, ReservationStatus.RESERVED)).willReturn(true);

        ClinicBulkAssignResponse response = service.bulkAssign(new ClinicBulkAssignRequest(
            TUESDAY, tue, tue, null, List.of(88L, 91L)));

        assertThat(response.clinics()).isEqualTo(1);
        assertThat(response.reservations()).isZero();
        assertThat(response.skipped()).isEmpty();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("기간이 뒤집히면 400이다")
    void 기간이_뒤집히면_거절한다() {
        assertThatThrownBy(() -> service.bulkAssign(new ClinicBulkAssignRequest(
            TUESDAY, tue, tue.minusDays(1), null, List.of(88L))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("일괄 해제는 그 요일만, 오늘부터, 출결이 기록된 예약은 남긴다")
    void 일괄_해제() {
        LocalDate today = LocalDate.now();
        LocalDate to = tue.plusWeeks(1);
        Clinic tueClinic = Fixtures.clinic(11L, tue, LocalTime.of(17, 0), null);
        Clinic nextTue = Fixtures.clinic(15L, tue.plusWeeks(1), LocalTime.of(17, 0), null);
        Clinic wednesday = Fixtures.clinic(12L, tue.plusDays(1), LocalTime.of(17, 0), null);
        ClinicReservation plain = Fixtures.reservation(1L, tueClinic, seo, teacher);
        ClinicReservation attended = Fixtures.reservation(2L, nextTue, kim, teacher);
        attended.checkAttendance(AttendanceStatus.PRESENT, null, teacher, OffsetDateTime.now());
        ClinicReservation otherDay = Fixtures.reservation(3L, wednesday, seo, teacher);
        // 기간 시작이 지난 날이어도 오늘부터 찾는다
        given(reservationRepository.findReservedForStudentsInRange(List.of(88L, 91L), today, to))
            .willReturn(List.of(plain, attended, otherDay));

        ClinicBulkUnassignResponse response = service.bulkUnassign(
            new ClinicBulkUnassignRequest(TUESDAY, pastTue, to, List.of(88L, 91L)));

        assertThat(response).isEqualTo(new ClinicBulkUnassignResponse(1, 1));
        assertThat(plain.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(attended.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(otherDay.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(reservationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("기간이 전부 지난 날이면 아무것도 안 한다")
    void 지난_기간은_해제하지_않는다() {
        ClinicBulkUnassignResponse response = service.bulkUnassign(
            new ClinicBulkUnassignRequest(TUESDAY, pastTue.minusWeeks(1), pastTue, List.of(88L)));

        assertThat(response).isEqualTo(new ClinicBulkUnassignResponse(0, 0));
        verify(reservationRepository, never()).findReservedForStudentsInRange(any(), any(), any());
    }
}
