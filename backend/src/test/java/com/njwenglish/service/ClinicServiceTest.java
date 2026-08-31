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
import com.njwenglish.entity.Clinic;
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

    private ClinicService clinicService;

    /** 2026-09-01은 화요일이다. 아래 테스트가 전부 이 달력에 기댄다. */
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);
    private static final short TUESDAY = 2;

    @BeforeEach
    void setUp() {
        clinicService = new ClinicService(clinicRepository, reservationRepository,
            teacherRepository, studentAccessGuard);
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
}
