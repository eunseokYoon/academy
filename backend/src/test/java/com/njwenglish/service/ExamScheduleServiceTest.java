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
import com.njwenglish.dto.score.ExamScheduleCreateRequest;
import com.njwenglish.dto.score.NextExamResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.enums.ExamType;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.ExamScheduleRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ExamScheduleServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private ExamScheduleRepository examScheduleRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private ExamScheduleService examScheduleService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");

    @BeforeEach
    void setUp() {
        examScheduleService = new ExamScheduleService(examScheduleRepository, classRoomRepository,
            enrollmentRepository, studentAccessGuard);
    }

    private ExamSchedule schedule(Long id, LocalDate startDate) {
        ExamSchedule schedule = ExamSchedule.create(classRoom, (short) 2026, (short) 1,
            ExamType.FINAL, startDate, startDate.plusDays(5), "교과서 5~8과");
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    @Test
    @DisplayName("D-day는 학생이 재원 중인 반 기준으로 계산된다")
    void D_day는_재원_중인_반_기준이다() {
        LocalDate today = LocalDate.now(KST);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L, 7L));
        given(examScheduleRepository.findNext(List.of(3L, 7L), today))
            .willReturn(Optional.of(schedule(12L, today.plusDays(27))));

        Optional<NextExamResponse> next = examScheduleService.findNextExam(88L);

        assertThat(next).isPresent();
        assertThat(next.get().dDay()).isEqualTo(27);
        assertThat(next.get().examType()).isEqualTo(ExamType.FINAL);
    }

    @Test
    @DisplayName("시험 일정이 없으면 D-day는 empty다 — 0을 채우지 않는다")
    void 일정이_없으면_D_day는_비어_있다() {
        LocalDate today = LocalDate.now(KST);
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of(3L));
        given(examScheduleRepository.findNext(List.of(3L), today)).willReturn(Optional.empty());

        assertThat(examScheduleService.findNextExam(88L)).isEmpty();
    }

    @Test
    @DisplayName("재원 중인 반이 없으면 조회 없이 empty다")
    void 재원_반이_없으면_조회하지_않는다() {
        given(enrollmentRepository.findActiveClassRoomIds(88L)).willReturn(List.of());

        assertThat(examScheduleService.findNextExam(88L)).isEmpty();

        verify(examScheduleRepository, never()).findNext(any(), any());
    }

    @Test
    @DisplayName("같은 반·연도·학기·시험종류를 다시 등록하면 409다")
    void 중복_등록은_409다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(examScheduleRepository.existsByClassRoomIdAndYearAndSemesterAndExamType(
            3L, (short) 2026, (short) 1, ExamType.FINAL)).willReturn(true);

        assertThatThrownBy(() -> examScheduleService.create(new ExamScheduleCreateRequest(
            3L, (short) 2026, (short) 1, ExamType.FINAL,
            LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 30), "교과서 5~8과")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

        verify(examScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 400이다")
    void 기간이_뒤집히면_400이다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));

        assertThatThrownBy(() -> examScheduleService.create(new ExamScheduleCreateRequest(
            3L, (short) 2026, (short) 1, ExamType.FINAL,
            LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 25), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }


    // ---------- 수정 (2026-10-03) ----------

    @Test
    @DisplayName("시험 범위만 고치면 기간은 그대로다")
    void 범위만_고친다() {
        ExamSchedule existing = schedule(12L, LocalDate.of(2026, 6, 25));
        given(examScheduleRepository.findWithClassRoom(12L)).willReturn(Optional.of(existing));

        examScheduleService.update(12L, new com.njwenglish.dto.score.ExamScheduleUpdateRequest(
            null, null, "  교과서 5~9과, 부교재 3강  "));

        assertThat(existing.getScopeNote()).isEqualTo("교과서 5~9과, 부교재 3강");
        assertThat(existing.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(existing.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("범위가 null이면 그대로, 빈 문자열이면 지운다")
    void 빈_범위는_지운다() {
        ExamSchedule existing = schedule(12L, LocalDate.of(2026, 6, 25));
        given(examScheduleRepository.findWithClassRoom(12L)).willReturn(Optional.of(existing));

        examScheduleService.update(12L, new com.njwenglish.dto.score.ExamScheduleUpdateRequest(
            LocalDate.of(2026, 6, 26), null, null));
        assertThat(existing.getScopeNote()).isEqualTo("교과서 5~8과");

        examScheduleService.update(12L, new com.njwenglish.dto.score.ExamScheduleUpdateRequest(
            null, null, "   "));
        assertThat(existing.getScopeNote()).isNull();
    }
}
