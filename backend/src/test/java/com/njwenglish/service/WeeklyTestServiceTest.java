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
import com.njwenglish.dto.weeklytest.WeeklyTestGridResponse;
import com.njwenglish.dto.weeklytest.WeeklyTestSaveRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeeklyTestServiceTest {

    @Mock
    private WeeklyTestRepository weeklyTestRepository;
    @Mock
    private WeeklyTestScoreRepository weeklyTestScoreRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private WeeklyTestService weeklyTestService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "목요일반", "ABCD12");
    private final Student hanul = Fixtures.student(88L, "김하늘");
    private final Student seojun = Fixtures.student(91L, "이서준");

    @BeforeEach
    void setUp() {
        weeklyTestService = new WeeklyTestService(weeklyTestRepository, weeklyTestScoreRepository,
            enrollmentRepository, classRoomRepository, studentAccessGuard);
    }

    private WeeklyTest wordTest(Long id) {
        WeeklyTest test = WeeklyTest.create(classRoom, WeeklyTestType.WORD,
            (short) 2026, (short) 5, (short) 3, (short) 25, null, null);
        ReflectionTestUtils.setField(test, "id", id);
        return test;
    }

    @Test
    @DisplayName("입력이 없는 종류도 열로 내려간다 — 프론트가 4열을 고정으로 그린다")
    void 열은_항상_네_종류다() {
        given(enrollmentRepository.findActiveStudents(anyLong(), any(LocalDate.class)))
            .willReturn(List.of(hanul));
        given(weeklyTestScoreRepository.findStudentsWithScores(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of());
        given(weeklyTestRepository.findByClassRoomIdAndYearAndMonthAndWeek(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of());

        WeeklyTestGridResponse grid = weeklyTestService.grid(3L, (short) 2026,
            (short) 5, (short) 3);

        assertThat(grid.tests()).hasSize(4);
        assertThat(grid.tests()).extracting(WeeklyTestGridResponse.TestColumn::testType)
            .containsExactly(WeeklyTestType.WORD, WeeklyTestType.REVIEW,
                WeeklyTestType.PRACTICE, WeeklyTestType.CLINIC);
        assertThat(grid.tests().get(0).cells()).isEmpty();
    }

    @Test
    @DisplayName("퇴원생이라도 그 주차에 성적이 있으면 명단에 나온다 — enrolled=false")
    void 퇴원생도_성적이_있으면_명단에_나온다() {
        given(enrollmentRepository.findActiveStudents(anyLong(), any(LocalDate.class)))
            .willReturn(List.of(hanul));
        given(weeklyTestScoreRepository.findStudentsWithScores(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of(seojun));
        given(weeklyTestRepository.findByClassRoomIdAndYearAndMonthAndWeek(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of());

        WeeklyTestGridResponse grid = weeklyTestService.grid(3L, (short) 2026,
            (short) 5, (short) 3);

        assertThat(grid.students()).extracting(WeeklyTestGridResponse.StudentRow::name)
            .containsExactly("김하늘", "이서준");
        assertThat(grid.students()).extracting(WeeklyTestGridResponse.StudentRow::enrolled)
            .containsExactly(true, false);
    }

    @Test
    @DisplayName("저장된 셀이 열에 실려 내려간다 — 다시 열면 값이 그대로 채워져 있어야 한다")
    void 저장된_셀이_내려간다() {
        WeeklyTest test = wordTest(10L);
        WeeklyTestScore cell = WeeklyTestScore.create(test, hanul, (short) 23, null, null,
            TestResult.PASS, false);

        given(enrollmentRepository.findActiveStudents(anyLong(), any(LocalDate.class)))
            .willReturn(List.of(hanul));
        given(weeklyTestScoreRepository.findStudentsWithScores(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of());
        given(weeklyTestRepository.findByClassRoomIdAndYearAndMonthAndWeek(3L, (short) 2026,
            (short) 5, (short) 3)).willReturn(List.of(test));
        given(weeklyTestScoreRepository.findByWeeklyTestIdIn(List.of(10L)))
            .willReturn(List.of(cell));

        WeeklyTestGridResponse grid = weeklyTestService.grid(3L, (short) 2026,
            (short) 5, (short) 3);

        WeeklyTestGridResponse.TestColumn word = grid.tests().get(0);
        assertThat(word.totalCount()).isEqualTo((short) 25);
        assertThat(word.cells()).hasSize(1);
        assertThat(word.cells().get(0).correctCount()).isEqualTo((short) 23);
        assertThat(word.cells().get(0).result()).isEqualTo(TestResult.PASS);
    }

    private WeeklyTestSaveRequest saveRequest(WeeklyTestSaveRequest.TestInput... tests) {
        return new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            List.of(tests));
    }

    @Test
    @DisplayName("맞힌 개수가 전체 문항 수보다 크면 400이다")
    void 맞힌_개수가_전체보다_크면_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 25, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, (short) 30, null, null,
                TestResult.PASS, false))));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("리뷰 테스트에 맞힌 개수를 실어 보내면 400이다 — Pass/Fail만 받는다")
    void 리뷰에_개수를_보내면_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.REVIEW, null, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, (short) 20, null, null,
                TestResult.PASS, false))));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("재시험 통과 체크는 FAIL일 때만 붙는다 — PASS면 400")
    void 재시험_통과는_FAIL일_때만이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 25, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, (short) 23, null, null,
                TestResult.PASS, true))));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("WORD·PRACTICE는 전체 문항 수 없이 셀을 저장할 수 없다")
    void 전체_문항_수가_없으면_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, null, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, (short) 23, null, null,
                TestResult.PASS, false))));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("값이 전부 빈 셀은 행을 지운다 — 학부모 화면에서 사라져야 한다")
    void 빈_셀은_행을_지운다() {
        WeeklyTest test = wordTest(10L);
        WeeklyTestScore existing = WeeklyTestScore.create(test, hanul, (short) 23, null, null,
            TestResult.PASS, false);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.WORD, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(test));
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(10L, 88L))
            .willReturn(Optional.of(existing));

        weeklyTestService.save(saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 25, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, null, null, null, null, false)))));

        verify(weeklyTestScoreRepository).delete(existing);
    }

    @Test
    @DisplayName("헤더값을 비워 보내면 그 종류를 통째로 지운다")
    void 헤더를_비우면_종류가_삭제된다() {
        WeeklyTest test = wordTest(10L);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.WORD, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(test));

        weeklyTestService.save(saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, null, null, null, List.of())));

        verify(weeklyTestRepository).delete(test);
    }

    // Critical #1 회귀: 셀 값 하한 검증
    @Test
    @DisplayName("셀의 correctCount가 음수면 400이다")
    void 음수_correctCount는_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 25, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, (short) -1, null, null,
                TestResult.PASS, false))));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    // Critical #2 회귀: 헤더 하한 검증 (셀 없음)
    @Test
    @DisplayName("WORD에 totalCount=0이고 셀이 없어도 400이다")
    void WORD_totalCount_0은_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 0, null, null, List.of()));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    // Critical #2 회귀: CLINIC 헤더 하한 검증
    @Test
    @DisplayName("CLINIC에 internalTotal=0이면 400이다")
    void CLINIC_internalTotal_0은_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.CLINIC, null, (short) 0, (short) 20, List.of()));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    // 회귀: 헤더값이 전부 null이면 종류 삭제
    @Test
    @DisplayName("헤더값 전부 null이면 종류가 삭제된다 (기존 동작 회귀)")
    void 헤더_전부_null이면_삭제된다() {
        WeeklyTest test = wordTest(10L);
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.WORD, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(test));

        weeklyTestService.save(saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, null, null, null, List.of())));

        verify(weeklyTestRepository).delete(test);
    }
}
