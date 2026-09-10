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
import com.njwenglish.dto.clinic.ClinicBulkCreateRequest;
import com.njwenglish.dto.clinic.ClinicBulkCreateResponse;
import com.njwenglish.dto.clinic.ClinicListItemResponse;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.enums.ReservationStatus;
import com.njwenglish.repository.ClinicChangeLogRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 클리닉 일괄 개설. LessonService.bulkCreate와 같은 규칙이다 —
 * <b>충돌하는 날짜는 건너뛰고 계속한다.</b>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicServiceTest {

    @Mock
    private ClinicRepository clinicRepository;
    @Mock
    private ClinicReservationRepository reservationRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private ClinicChangeLogRepository changeLogRepository;

    private ClinicService clinicService;

    /** 2026-09-01은 화요일이다. 아래 테스트가 전부 이 달력에 기댄다. */
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);
    private static final short TUESDAY = 2;

    @BeforeEach
    void setUp() {
        clinicService = new ClinicService(clinicRepository, reservationRepository,
            teacherRepository, studentAccessGuard, changeLogRepository);
        given(teacherRepository.findByUserId(any()))
            .willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        Fixtures.login(Fixtures.teacher(1L));
        given(clinicRepository.save(any(Clinic.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ClinicBulkCreateRequest request(LocalDate from, LocalDate to,
                                            LocalTime start, LocalTime end,
                                            List<LocalDate> skipDates) {
        return new ClinicBulkCreateRequest(TUESDAY, from, to, start, end,
            (short) 6, null, skipDates);
    }

    @Test
    @DisplayName("기간 안의 그 요일에만 만든다")
    void 지정한_요일에만_만든다() {
        ClinicBulkCreateResponse response = clinicService.bulkCreate(
            request(SEP_1, SEP_30, LocalTime.of(17, 0), LocalTime.of(22, 0), null));

        // 2026년 9월의 화요일은 1·8·15·22·29 — 다섯 번이다
        assertThat(response.created()).isEqualTo(5);
        assertThat(response.createdDates())
            .containsExactly(SEP_1, SEP_1.plusWeeks(1), SEP_1.plusWeeks(2),
                SEP_1.plusWeeks(3), SEP_1.plusWeeks(4));
    }

    @Test
    @DisplayName("이미 OPEN인 날짜는 건너뛰고 나머지는 만든다")
    void 이미_열려_있는_날짜는_건너뛴다() {
        // 전부 실패시키면 선생님이 걸린 날짜를 찾아 지우고 다시 눌러야 한다
        given(clinicRepository.findOpenDates(SEP_1, SEP_30, LocalTime.of(17, 0)))
            .willReturn(List.of(SEP_1.plusWeeks(1)));

        ClinicBulkCreateResponse response = clinicService.bulkCreate(
            request(SEP_1, SEP_30, LocalTime.of(17, 0), LocalTime.of(22, 0), null));

        assertThat(response.created()).isEqualTo(4);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.createdDates()).doesNotContain(SEP_1.plusWeeks(1));
    }

    @Test
    @DisplayName("skipDates로 지정한 날은 빠진다")
    void skipDates는_빠진다() {
        ClinicBulkCreateResponse response = clinicService.bulkCreate(
            request(SEP_1, SEP_30, LocalTime.of(17, 0), LocalTime.of(22, 0),
                List.of(SEP_1, SEP_1.plusWeeks(4))));

        assertThat(response.created()).isEqualTo(3);
        assertThat(response.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("to가 from보다 이르면 400이다")
    void 기간이_뒤집히면_400이다() {
        assertThatThrownBy(() -> clinicService.bulkCreate(
            request(SEP_30, SEP_1, LocalTime.of(17, 0), LocalTime.of(22, 0), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(clinicRepository, never()).save(any());
    }

    @Test
    @DisplayName("슬롯이 하나도 안 나오는 시간대는 400이다")
    void 슬롯이_없으면_400이다() {
        // ck_clinics_time은 end > start만 본다. 17:00~17:30은 통과하지만
        // 학생이 고를 도착 시각이 없는 클리닉이 만들어진다
        assertThatThrownBy(() -> clinicService.bulkCreate(
            request(SEP_1, SEP_30, LocalTime.of(17, 0), LocalTime.of(17, 30), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(clinicRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료가 시작보다 이르면 400이다")
    void 시간이_뒤집히면_400이다() {
        assertThatThrownBy(() -> clinicService.bulkCreate(
            request(SEP_1, SEP_30, LocalTime.of(22, 0), LocalTime.of(17, 0), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    /** 프로젝션 인터페이스는 인스턴스를 만들 수 없어 테스트용 구현을 둔다. */
    private static ClinicReservationRepository.SlotCount slotCount(Long clinicId, LocalTime at,
                                                                   long reserved, long pending) {
        return new ClinicReservationRepository.SlotCount() {
            @Override public Long getClinicId() { return clinicId; }
            @Override public LocalTime getArrivalTime() { return at; }
            @Override public long getReservedCount() { return reserved; }
            @Override public long getPendingCount() { return pending; }
        };
    }

    @Test
    @DisplayName("한 슬롯만 확정하면 클리닉은 아직 확정이 아니다 — 18시 명단이 남아 있다")
    void clinicIsConfirmedOnlyWhenEveryStudentSlotIsDone() {
        Clinic wide = Clinic.open(Fixtures.teacherEntity(1L), SEP_1,
            LocalTime.of(17, 0), LocalTime.of(22, 0), (short) 6, null);
        ReflectionTestUtils.setField(wide, "id", 41L);
        given(clinicRepository.findInRange(any(), any(), any())).willReturn(List.of(wide));
        given(reservationRepository.countReservedByClinicIds(List.of(41L))).willReturn(List.of());
        given(reservationRepository.findAttendanceConfirmedClinicIds(List.of(41L)))
            .willReturn(List.of());
        // 17시 2명 전원 확정, 18시 1명 미확정. 나머지 슬롯(19·20·21시)은 배정이 없어 행이 없다
        given(reservationRepository.countBySlot(List.of(41L))).willReturn(List.of(
            slotCount(41L, LocalTime.of(17, 0), 2, 0),
            slotCount(41L, LocalTime.of(18, 0), 1, 1)));

        List<ClinicListItemResponse> result = clinicService.listForTeacher(2026, 9, 1, null);

        ClinicListItemResponse item = result.get(0);
        // 분모가 5가 아니다 — 배정 없는 시각은 확정할 것이 없어 세지 않는다
        assertThat(item.studentSlotCount()).isEqualTo(2);
        assertThat(item.confirmedSlotCount()).isEqualTo(1);
        assertThat(item.attendanceConfirmed()).isFalse();
    }

    /** 배정 해제(unassign)는 행을 지우지 않고 CANCELED로 바꾼다. 그 행이 FK로 삭제를 막고 있었다. */
    @Test
    @DisplayName("배정 해제로 남은 예약 행이 있어도 지운다")
    void 취소된_예약만_남았으면_지운다() {
        Clinic clinic = Fixtures.clinic(7L, SEP_1, LocalTime.of(17, 0), (short) 6);
        given(clinicRepository.findById(7L)).willReturn(Optional.of(clinic));
        given(reservationRepository.countByClinicIdAndStatus(7L, ReservationStatus.RESERVED))
            .willReturn(0L);
        given(changeLogRepository.existsByFromClinicIdOrToClinicId(7L, 7L)).willReturn(false);

        clinicService.delete(7L);

        verify(reservationRepository)
            .deleteByClinicIdAndStatusNot(7L, ReservationStatus.RESERVED);
        verify(clinicRepository).delete(clinic);
    }

    @Test
    @DisplayName("배정된 학생이 있으면 409이고 삭제하지 않는다")
    void 배정된_학생이_있으면_거절한다() {
        Clinic clinic = Fixtures.clinic(7L, SEP_1, LocalTime.of(17, 0), (short) 6);
        given(clinicRepository.findById(7L)).willReturn(Optional.of(clinic));
        given(reservationRepository.countByClinicIdAndStatus(7L, ReservationStatus.RESERVED))
            .willReturn(3L);

        assertThatThrownBy(() -> clinicService.delete(7L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLINIC_HAS_RECORDS);

        // 거절된 요청에서 예약 행이 지워지면 안 된다 — 이 태스크가 막는 회귀가
        // "purge가 409 검사보다 먼저 도는 것"이다
        verify(reservationRepository, never())
            .deleteByClinicIdAndStatusNot(any(), any());
        verify(clinicRepository, never()).delete(any(Clinic.class));
    }

    /**
     * 변경 이력은 쓰기 전용 감사 기록이라 지우면 안 되고, from_clinic_id가 NOT NULL이라
     * null로 비울 수도 없다. 이동이 얽힌 클리닉은 닫아야 한다 — 500이 아니라 409로 알린다.
     */
    @Test
    @DisplayName("변경 이력이 걸려 있으면 409다")
    void 변경_이력이_있으면_거절한다() {
        Clinic clinic = Fixtures.clinic(7L, SEP_1, LocalTime.of(17, 0), (short) 6);
        given(clinicRepository.findById(7L)).willReturn(Optional.of(clinic));
        given(reservationRepository.countByClinicIdAndStatus(7L, ReservationStatus.RESERVED))
            .willReturn(0L);
        given(changeLogRepository.existsByFromClinicIdOrToClinicId(7L, 7L)).willReturn(true);

        assertThatThrownBy(() -> clinicService.delete(7L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CLINIC_HAS_RECORDS);

        verify(reservationRepository, never())
            .deleteByClinicIdAndStatusNot(any(), any());
        verify(clinicRepository, never()).delete(any(Clinic.class));
    }
}
