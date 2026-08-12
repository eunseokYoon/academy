package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import com.njwenglish.dto.clinic.ClinicAssignRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmResponse;
import com.njwenglish.dto.clinic.ClinicReservationChangeRequest;
import com.njwenglish.dto.clinic.ClinicReservationCreateRequest;
import com.njwenglish.dto.clinic.ClinicReservationCreateResponse;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ClinicStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.entity.ClinicChangeLog;
import com.njwenglish.repository.ClinicChangeLogRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClinicReservationServiceTest {

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

    private ClinicReservationService clinicReservationService;

    private final Student seo = Fixtures.student(88L, "서동환");
    private final Student kim = Fixtures.student(91L, "김하늘");
    private final Student park = Fixtures.student(97L, "박서준");
    private final Teacher teacher = Fixtures.teacherEntity(1L);

    /** Fixtures.clinic은 시작 + 1시간짜리라 고를 수 있는 슬롯이 시작 시각 하나뿐이다. */
    private static final ClinicReservationCreateRequest AT_17 =
        new ClinicReservationCreateRequest(LocalTime.of(17, 0));

    /** 정원 6, 넉넉히 미래 날짜. 17:00~18:00. */
    private Clinic clinic;

    @BeforeEach
    void setUp() {
        clinic = Fixtures.clinic(41L, LocalDate.now().plusDays(7), LocalTime.of(17, 0), (short) 6);
        clinicReservationService = new ClinicReservationService(clinicRepository,
            reservationRepository, changeLogRepository, noticeService, studentRepository,
            teacherRepository, studentAccessGuard);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void givenLockedClinic(long reservedCount) {
        given(clinicRepository.findByIdForUpdate(41L)).willReturn(Optional.of(clinic));
        given(reservationRepository.countByClinicIdAndStatus(41L, ReservationStatus.RESERVED))
            .willReturn(reservedCount);
    }

    private void givenSaveEchoes() {
        given(reservationRepository.save(any(ClinicReservation.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("학생 신청은 클리닉 행을 FOR UPDATE로 잠근 뒤에 센다")
    void 신청은_클리닉을_잠근_뒤_센다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));
        givenLockedClinic(4);
        givenSaveEchoes();

        ClinicReservationCreateResponse response = clinicReservationService.reserve(41L, AT_17);

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        // 잠그지 않고 세면 동시 신청 시 두 트랜잭션이 같은 count를 읽어 정원을 넘긴다
        verify(clinicRepository).findByIdForUpdate(41L);
        // assigned_by는 null이어야 학생 본인 신청으로 구분된다
        verify(reservationRepository).save(any(ClinicReservation.class));
    }

    @Test
    @DisplayName("정원이 찬 클리닉에 신청하면 409 CLINIC_CAPACITY_EXCEEDED")
    void 정원이_차면_거절된다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));
        givenLockedClinic(6);

        assertThatThrownBy(() -> clinicReservationService.reserve(41L, AT_17))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLINIC_CAPACITY_EXCEEDED);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("중복 신청은 정원 초과와 다른 코드다 — S-9 문구가 갈린다")
    void 중복_신청은_DUPLICATE_RESOURCE다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));
        given(clinicRepository.findByIdForUpdate(41L)).willReturn(Optional.of(clinic));
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            41L, 88L, ReservationStatus.RESERVED)).willReturn(true);

        assertThatThrownBy(() -> clinicReservationService.reserve(41L, AT_17))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("capacity가 null이면 인원 제한이 없다")
    void 정원이_null이면_제한이_없다() {
        Clinic unlimited = Fixtures.clinic(42L, LocalDate.now().plusDays(7),
            LocalTime.of(19, 0), null);
        given(clinicRepository.findByIdForUpdate(42L)).willReturn(Optional.of(unlimited));
        given(reservationRepository.countByClinicIdAndStatus(42L, ReservationStatus.RESERVED))
            .willReturn(99L);
        givenSaveEchoes();

        List<ClinicReservation> created = clinicReservationService.reserveLocked(
            42L, List.of(seo), null, LocalTime.of(19, 0), true);

        assertThat(created).hasSize(1);
    }

    @Test
    @DisplayName("닫힌 시간대에는 신청할 수 없다")
    void 닫힌_클리닉에는_신청할_수_없다() {
        clinic.changeStatus(ClinicStatus.CLOSED);
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));

        assertThatThrownBy(() -> clinicReservationService.reserve(41L, AT_17))
            .isInstanceOf(BusinessException.class);

        verify(clinicRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("지난 날짜 클리닉 신청은 400이다")
    void 지난_날짜는_신청할_수_없다() {
        Clinic past = Fixtures.clinic(43L, LocalDate.now().minusDays(1),
            LocalTime.of(17, 0), (short) 6);
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(43L)).willReturn(Optional.of(past));

        assertThatThrownBy(() -> clinicReservationService.reserve(43L, AT_17))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("선생님 배정 해제는 행을 지우지 않고 CANCELED로 바꾼다 — 나중에 다시 신청할 수 있다")
    void 배정_해제는_상태만_바꾼다() {
        // 학생에게는 취소 경로가 없다(2026-08-10 확정). 명단에서 빼는 건 선생님뿐이다
        ClinicReservation reservation = Fixtures.reservation(902L, clinic, seo, null);
        given(reservationRepository.findByClinicIdAndStudentIdAndStatus(
            41L, 88L, ReservationStatus.RESERVED)).willReturn(Optional.of(reservation));

        clinicReservationService.unassign(41L, 88L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        verify(reservationRepository, never()).delete(any());
        // 선생님이 직접 뺀 것이라 학생에게 알릴 공지가 없다
        verify(changeLogRepository, never()).save(any());
        verify(noticeService, never()).publishForStudent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("배정이 정원을 넘으면 일부만 넣지 않고 전체가 409로 거절된다")
    void 배정이_정원을_넘으면_전체가_거절된다() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(studentRepository.findById(88L)).willReturn(Optional.of(seo));
        given(studentRepository.findById(91L)).willReturn(Optional.of(kim));
        given(studentRepository.findById(97L)).willReturn(Optional.of(park));
        givenLockedClinic(4);   // 4 + 3 = 7 > 6

        assertThatThrownBy(() -> clinicReservationService.assign(41L,
            new ClinicAssignRequest(List.of(88L, 91L, 97L), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLINIC_CAPACITY_EXCEEDED);

        // 넘는 만큼만 넣으면 선생님이 누가 빠졌는지 모른다
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("배정은 이미 신청한 학생을 건너뛰고 assigned_by를 남긴다")
    void 배정은_멱등이고_배정자를_남긴다() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(studentRepository.findById(88L)).willReturn(Optional.of(seo));
        given(studentRepository.findById(91L)).willReturn(Optional.of(kim));
        given(clinicRepository.findByIdForUpdate(41L)).willReturn(Optional.of(clinic));
        // 서동환은 이미 본인이 신청해 둔 상태다
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            41L, 88L, ReservationStatus.RESERVED)).willReturn(true);
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            41L, 91L, ReservationStatus.RESERVED)).willReturn(false);
        given(reservationRepository.countByClinicIdAndStatus(41L, ReservationStatus.RESERVED))
            .willReturn(1L);
        givenSaveEchoes();
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));
        given(reservationRepository.findReservedWithStudent(41L)).willReturn(List.of());

        clinicReservationService.assign(41L, new ClinicAssignRequest(List.of(88L, 91L), null));

        // 중복은 조용히 건너뛴다(멱등). 새로 들어가는 건 김하늘 한 명뿐이다
        verify(reservationRepository).save(any(ClinicReservation.class));
    }

    @Test
    @DisplayName("클리닉 출석은 attend_status에만 기록된다 — attendances 테이블은 건드리지 않는다")
    void 클리닉_출석은_예약_행에_기록된다() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(clinicRepository.findById(41L)).willReturn(Optional.of(clinic));

        ClinicReservation seoReservation = Fixtures.reservation(902L, clinic, seo, null);
        ClinicReservation kimReservation = Fixtures.reservation(903L, clinic, kim, teacher);
        given(reservationRepository.findReservedWithStudent(41L))
            .willReturn(List.of(seoReservation, kimReservation));

        ClinicAttendanceConfirmResponse response = clinicReservationService.confirmAttendance(41L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, "무단"))));

        // 안 온 학생만 보내고 나머지는 PRESENT로 채운다 (T-5와 같은 방식)
        assertThat(seoReservation.getAttendStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(kimReservation.getAttendStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(kimReservation.getMemo()).isEqualTo("무단");
        assertThat(seoReservation.getCheckedBy()).isSameAs(teacher);
        assertThat(response.summary().present()).isEqualTo(1);
        assertThat(response.summary().absent()).isEqualTo(1);
    }

    // ---------- 도착 시각 · 변경 (2026-08-10) ----------

    /** 17:00~22:00. 슬롯이 17·18·19·20·21시 다섯 개다. */
    private Clinic wideClinic(Long id) {
        Clinic wide = Clinic.open(teacher, LocalDate.now().plusDays(7),
            LocalTime.of(17, 0), LocalTime.of(22, 0), (short) 6, null);
        ReflectionTestUtils.setField(wide, "id", id);
        return wide;
    }

    @Test
    @DisplayName("슬롯 목록에 없는 도착 시각은 400이다 — 화면이 막아 줄 거라고 믿지 않는다")
    void arrivalTimeOutsideSlotsIsRejected() {
        Clinic wide = wideClinic(50L);
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(clinicRepository.findById(50L)).willReturn(Optional.of(wide));
        given(clinicRepository.findByIdForUpdate(50L)).willReturn(Optional.of(wide));

        // 22:00은 마지막 슬롯(21:00) 다음이라 없는 값이다
        assertThatThrownBy(() -> clinicReservationService.reserve(50L,
            new ClinicReservationCreateRequest(LocalTime.of(22, 0))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 클리닉 안에서 시간을 바꾸면 즉시 반영되고 사유가 기록된다")
    void changingArrivalTimeAppliesImmediatelyAndLogs() {
        Clinic wide = wideClinic(50L);
        ClinicReservation reservation = ClinicReservation.reserve(wide, seo, null,
            LocalTime.of(17, 0));
        ReflectionTestUtils.setField(reservation, "id", 902L);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findByClinicIdAndStudentIdAndStatus(
            50L, 88L, ReservationStatus.RESERVED)).willReturn(Optional.of(reservation));

        clinicReservationService.change(50L, new ClinicReservationChangeRequest(
            null, LocalTime.of(20, 0), "  학원 셔틀이 늦어져요  "));

        // 승인 대기 상태 없이 곧바로 바뀐다
        assertThat(reservation.getArrivalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);

        ArgumentCaptor<ClinicChangeLog> captor = ArgumentCaptor.forClass(ClinicChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        ClinicChangeLog log = captor.getValue();
        // from을 안 남기면 예약이 이미 바뀐 뒤라 원래 시각을 복원할 수 없다
        assertThat(log.getFromArrivalTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(log.getToArrivalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(log.isCancel()).isFalse();
        assertThat(log.getReason()).isEqualTo("학원 셔틀이 늦어져요");

        // 승인 절차가 없어서 학부모가 아는 길은 공지뿐이다. 사유가 본문에 들어가야 한다
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(noticeService).publishForStudent(
            eq("클리닉 시간 변경 안내"), body.capture(), eq(seo), any());
        assertThat(body.getValue()).contains("17:00", "20:00", "학원 셔틀이 늦어져요");
    }

    @Test
    @DisplayName("다른 클리닉으로 옮기면 기존 예약이 MOVED가 되고 새 예약이 생긴다")
    void movingToAnotherClinicVacatesTheOldOne() {
        Clinic from = wideClinic(50L);
        Clinic to = wideClinic(51L);
        ClinicReservation reservation = ClinicReservation.reserve(from, seo, null,
            LocalTime.of(17, 0));
        ReflectionTestUtils.setField(reservation, "id", 902L);

        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findByClinicIdAndStudentIdAndStatus(
            50L, 88L, ReservationStatus.RESERVED)).willReturn(Optional.of(reservation));
        given(clinicRepository.findById(51L)).willReturn(Optional.of(to));
        given(clinicRepository.findByIdForUpdate(51L)).willReturn(Optional.of(to));
        given(reservationRepository.countByClinicIdAndStatus(51L, ReservationStatus.RESERVED))
            .willReturn(0L);
        givenSaveEchoes();

        clinicReservationService.change(50L, new ClinicReservationChangeRequest(
            51L, LocalTime.of(19, 0), "다른 날로 옮길게요"));

        // 먼저 비우지 않으면 같은 학생이 두 시간대에 RESERVED로 남는다
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.MOVED);
        verify(reservationRepository).save(any(ClinicReservation.class));
        // 목표 클리닉 정원도 다시 확인해야 한다
        verify(clinicRepository).findByIdForUpdate(51L);
    }

    @Test
    @DisplayName("남의 예약은 바꿀 수 없다 — 본인 RESERVED 행만 찾는다")
    void cannotChangeSomeoneElsesReservation() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findByClinicIdAndStudentIdAndStatus(
            41L, 88L, ReservationStatus.RESERVED)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clinicReservationService.change(41L,
            new ClinicReservationChangeRequest(null, LocalTime.of(17, 0), "사유")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

        verify(changeLogRepository, never()).save(any());
        verify(noticeService, never()).publishForStudent(any(), any(), any(), any());
    }
}
