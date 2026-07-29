package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.clinic.ClinicChangeRequestCreateRequest;
import com.njwenglish.dto.clinic.ClinicDecideRequest;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.ClinicChangeRequest;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicChangeRequestRepository;
import com.njwenglish.repository.ClinicRepository;
import com.njwenglish.repository.ClinicReservationRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClinicChangeRequestServiceTest {

    @Mock
    private ClinicChangeRequestRepository changeRequestRepository;
    @Mock
    private ClinicReservationRepository reservationRepository;
    @Mock
    private ClinicRepository clinicRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private ClinicChangeRequestService changeRequestService;
    private ClinicReservationService clinicReservationService;

    private final Student seo = Fixtures.student(88L, "서동환");
    private final Teacher teacher = Fixtures.teacherEntity(1L);

    private Clinic thursday;
    private Clinic saturday;
    private ClinicReservation reservation;

    @BeforeEach
    void setUp() {
        thursday = Fixtures.clinic(41L, LocalDate.now().plusDays(5), LocalTime.of(17, 0),
            (short) 6);
        saturday = Fixtures.clinic(47L, LocalDate.now().plusDays(7), LocalTime.of(19, 0),
            (short) 6);
        reservation = Fixtures.reservation(902L, thursday, seo, null);

        // 정원 재확인이 학생 신청과 같은 메서드를 타는지 보려면 실제 인스턴스를 써야 한다
        clinicReservationService = new ClinicReservationService(clinicRepository,
            reservationRepository, null, teacherRepository, studentAccessGuard);
        changeRequestService = new ClinicChangeRequestService(changeRequestRepository,
            reservationRepository, clinicRepository, teacherRepository, studentAccessGuard,
            clinicReservationService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ClinicChangeRequest pendingRequest(Clinic target) {
        ClinicChangeRequest request = ClinicChangeRequest.create(reservation, seo, target,
            "SCHOOL", "학교 보충수업");
        ReflectionTestUtils.setField(request, "id", 12L);
        return request;
    }

    private void loginAsTeacher() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
    }

    @Test
    @DisplayName("같은 예약에 PENDING 요청이 이미 있으면 409 — 둘 다 승인되면 예약이 꼬인다")
    void 중복_변경_요청은_409다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findById(902L)).willReturn(Optional.of(reservation));
        given(changeRequestRepository.existsByReservationIdAndStatus(
            902L, ChangeRequestStatus.PENDING)).willReturn(true);

        assertThatThrownBy(() -> changeRequestService.create(
            new ClinicChangeRequestCreateRequest(902L, 47L, "SCHOOL", null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

        verify(changeRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 예약에는 변경 요청을 걸 수 없다")
    void 남의_예약은_요청할_수_없다() {
        Student other = Fixtures.student(91L, "김하늘");
        given(studentAccessGuard.requireSelf()).willReturn(other);
        given(reservationRepository.findById(902L)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> changeRequestService.create(
            new ClinicChangeRequestCreateRequest(902L, 47L, "SCHOOL", null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDENT_NOT_ACCESSIBLE);
    }

    @Test
    @DisplayName("승인하면 기존 예약이 MOVED가 되고 목표 클리닉에 새 RESERVED 행이 생긴다")
    void 승인은_한_트랜잭션으로_옮긴다() {
        loginAsTeacher();
        ClinicChangeRequest request = pendingRequest(saturday);
        given(changeRequestRepository.findWithDetail(12L)).willReturn(Optional.of(request));
        given(clinicRepository.findByIdForUpdate(47L)).willReturn(Optional.of(saturday));
        given(reservationRepository.existsByClinicIdAndStudentIdAndStatus(
            47L, 88L, ReservationStatus.RESERVED)).willReturn(false);
        given(reservationRepository.countByClinicIdAndStatus(47L, ReservationStatus.RESERVED))
            .willReturn(2L);
        given(reservationRepository.save(any(ClinicReservation.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        var response = changeRequestService.decide(12L, new ClinicDecideRequest(true, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.MOVED);
        assertThat(response.status()).isEqualTo(ChangeRequestStatus.APPROVED);
        assertThat(request.getDecidedBy()).isSameAs(teacher);
        // 승인 시점의 정원 재확인도 학생 신청과 같은 락을 탄다
        verify(clinicRepository).findByIdForUpdate(47L);
        verify(reservationRepository).save(any(ClinicReservation.class));
    }

    @Test
    @DisplayName("승인 시점에 목표 클리닉이 꽉 차 있으면 409가 나고 요청은 PENDING으로 남는다")
    void 승인_시점에_정원이_차면_409다() {
        loginAsTeacher();
        ClinicChangeRequest request = pendingRequest(saturday);
        given(changeRequestRepository.findWithDetail(12L)).willReturn(Optional.of(request));
        given(clinicRepository.findByIdForUpdate(47L)).willReturn(Optional.of(saturday));
        given(reservationRepository.countByClinicIdAndStatus(47L, ReservationStatus.RESERVED))
            .willReturn(6L);

        assertThatThrownBy(() -> changeRequestService.decide(12L,
            new ClinicDecideRequest(true, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLINIC_CAPACITY_EXCEEDED);

        // 예외가 트랜잭션을 되돌리므로 status는 PENDING으로 남는다
        assertThat(request.getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
        assertThat(request.getDecidedAt()).isNull();
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("targetClinic이 null이면 취소 요청이라 기존 예약만 CANCELED가 된다")
    void 취소_요청_승인은_기존_예약만_지운다() {
        loginAsTeacher();
        ClinicChangeRequest request = pendingRequest(null);
        given(changeRequestRepository.findWithDetail(12L)).willReturn(Optional.of(request));

        changeRequestService.decide(12L, new ClinicDecideRequest(true, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
        assertThat(request.getStatus()).isEqualTo(ChangeRequestStatus.APPROVED);
        verify(clinicRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("거절하면 예약은 그대로 두고 요청만 REJECTED가 된다")
    void 거절은_예약을_건드리지_않는다() {
        loginAsTeacher();
        ClinicChangeRequest request = pendingRequest(saturday);
        given(changeRequestRepository.findWithDetail(12L)).willReturn(Optional.of(request));

        changeRequestService.decide(12L, new ClinicDecideRequest(false, null));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(request.getStatus()).isEqualTo(ChangeRequestStatus.REJECTED);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 처리된 요청은 다시 결정할 수 없다")
    void 이미_처리된_요청은_재결정할_수_없다() {
        ClinicChangeRequest request = pendingRequest(saturday);
        request.decide(true, teacher, java.time.OffsetDateTime.now());
        given(changeRequestRepository.findWithDetail(12L)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> changeRequestService.decide(12L,
            new ClinicDecideRequest(false, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("reason_code는 문자열 그대로 저장한다 — 옵션 목록이 미확정이라 검증하지 않는다")
    void 사유코드는_그대로_저장된다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findById(902L)).willReturn(Optional.of(reservation));
        given(clinicRepository.findById(47L)).willReturn(Optional.of(saturday));
        given(changeRequestRepository.save(any(ClinicChangeRequest.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        changeRequestService.create(
            new ClinicChangeRequestCreateRequest(902L, 47L, "아무값이나", "학교 보충"));

        var captor = org.mockito.ArgumentCaptor.forClass(ClinicChangeRequest.class);
        verify(changeRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getReasonCode()).isEqualTo("아무값이나");
        assertThat(captor.getValue().getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
    }

    @Test
    @DisplayName("같은 클리닉으로 옮기는 요청은 400이다")
    void 같은_클리닉으로는_옮길_수_없다() {
        given(studentAccessGuard.requireSelf()).willReturn(seo);
        given(reservationRepository.findById(902L)).willReturn(Optional.of(reservation));

        assertThatThrownBy(() -> changeRequestService.create(
            new ClinicChangeRequestCreateRequest(902L, 41L, "SCHOOL", null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("목록은 대기 중인 것만 걸러 준다")
    void 대기_목록을_내려준다() {
        given(changeRequestRepository.findAllWithDetail(ChangeRequestStatus.PENDING))
            .willReturn(List.of(pendingRequest(saturday)));

        var list = changeRequestService.list(ChangeRequestStatus.PENDING);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).from().clinicId()).isEqualTo(41L);
        assertThat(list.get(0).to().clinicId()).isEqualTo(47L);
        assertThat(list.get(0).studentName()).isEqualTo("서동환");
    }
}
