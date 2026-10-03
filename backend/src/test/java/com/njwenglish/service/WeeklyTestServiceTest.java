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
import com.njwenglish.dto.weeklytest.ClinicReflection;
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
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeeklyTestServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
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
            enrollmentRepository, classRoomRepository, studentAccessGuard, eventPublisher);
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
        return new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3, null,
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

    // Critical #2 추가 회귀: CLINIC 부분 채움 미검증
    @Test
    @DisplayName("CLINIC에서 한쪽만 채우면 400이다 — internalTotal만 있으면 거부")
    void CLINIC_internalTotal만_있으면_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.CLINIC, null, (short) 15, null, List.of()));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("CLINIC에서 한쪽만 채우면 400이다 — externalTotal만 있으면 거부")
    void CLINIC_externalTotal만_있으면_400이다() {
        WeeklyTestSaveRequest request = saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.CLINIC, null, null, (short) 20, List.of()));

        assertThatThrownBy(() -> weeklyTestService.save(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    // ---------- 온라인 클리닉 테스트 자동 반영 (2026-08-10) ----------

    private WeeklyTest clinicHeader(Long id, short internalTotal, short externalTotal) {
        WeeklyTest header = WeeklyTest.create(classRoom, WeeklyTestType.CLINIC,
            (short) 2026, (short) 8, (short) 1, null, internalTotal, externalTotal);
        ReflectionTestUtils.setField(header, "id", id);
        return header;
    }

    private void reflect(short internalTotal, short externalTotal) {
        weeklyTestService.reflectClinicScore(classRoom, (short) 2026, (short) 8, (short) 1,
            internalTotal, externalTotal, hanul, (short) 12, (short) 9);
    }

    @Test
    @DisplayName("클리닉 헤더가 없으면 온라인 테스트 문항 수로 만들고 칸을 채운다")
    void 헤더가_없으면_만들고_반영한다() {
        // 없음 → ON CONFLICT DO NOTHING으로 넣고 → 다시 읽으면 있다
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.empty(), Optional.of(clinicHeader(700L, (short) 15, (short) 15)));
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(700L, 88L))
            .willReturn(Optional.empty());

        reflect((short) 15, (short) 15);

        verify(weeklyTestRepository).insertClinicHeaderIfAbsent(3L, (short) 2026, (short) 8,
            (short) 1, (short) 15, (short) 15);
        verify(weeklyTestRepository, never()).save(any());

        ArgumentCaptor<WeeklyTestScore> captor = ArgumentCaptor.forClass(WeeklyTestScore.class);
        verify(weeklyTestScoreRepository).save(captor.capture());
        WeeklyTestScore cell = captor.getValue();
        assertThat(cell.getInternalCorrect()).isEqualTo((short) 12);
        assertThat(cell.getExternalCorrect()).isEqualTo((short) 9);
        // CLINIC은 correctCount·result를 쓰지 않는다
        assertThat(cell.getCorrectCount()).isNull();
        assertThat(cell.getResult()).isNull();
    }

    // 2026-09-30 리뷰: 같은 주차를 두 학생이 거의 동시에 내면 둘 다 「헤더 없음」을 읽고 save해
    // 한쪽이 uq_weekly_tests 위반으로 터졌고, 반영이 제출과 같은 트랜잭션이라 제출까지 롤백됐다
    @Test
    @DisplayName("동시에 낸 학생이 헤더를 먼저 만들었으면 그 헤더에 반영한다 — 제출이 터지지 않는다")
    void 동시에_만든_헤더에_반영한다() {
        WeeklyTest madeByOther = clinicHeader(700L, (short) 15, (short) 15);
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.empty(), Optional.of(madeByOther));
        // 충돌이라 넣은 행이 없다
        given(weeklyTestRepository.insertClinicHeaderIfAbsent(3L, (short) 2026, (short) 8,
            (short) 1, (short) 15, (short) 15)).willReturn(0);
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(700L, 88L))
            .willReturn(Optional.empty());

        reflect((short) 15, (short) 15);

        verify(weeklyTestRepository, never()).save(any());
        verify(weeklyTestScoreRepository).save(any(WeeklyTestScore.class));
    }

    @Test
    @DisplayName("동시에 만든 헤더의 문항 수가 다르면 반영하지 않는다")
    void 동시에_만든_헤더가_다르면_건너뛴다() {
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.empty(), Optional.of(clinicHeader(700L, (short) 10, (short) 10)));

        reflect((short) 15, (short) 15);

        verify(weeklyTestScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("헤더 문항 수가 다르면 반영하지 않는다 — 반 공통 값이라 한 명 때문에 덮을 수 없다")
    void 문항_수가_다르면_반영하지_않는다() {
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.of(clinicHeader(700L, (short) 15, (short) 15)));

        // 온라인 테스트는 내부 10 / 외부 10짜리다
        weeklyTestService.reflectClinicScore(classRoom, (short) 2026, (short) 8, (short) 1,
            (short) 10, (short) 10, hanul, (short) 8, (short) 7);

        verify(weeklyTestScoreRepository, never()).save(any());
        verify(weeklyTestRepository, never()).save(any());
    }

    @Test
    @DisplayName("선생님이 이미 적은 칸은 덮지 않는다")
    void 이미_적힌_칸은_덮지_않는다() {
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.of(clinicHeader(700L, (short) 15, (short) 15)));
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(700L, 88L))
            .willReturn(Optional.of(WeeklyTestScore.create(
                clinicHeader(700L, (short) 15, (short) 15), hanul,
                null, (short) 14, (short) 13, null, false)));

        reflect((short) 15, (short) 15);

        verify(weeklyTestScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("내부지문 문항 수가 없거나 한쪽이 0이면 반영하지 않는다 — 헤더 CHECK가 막는다")
    void 내부외부로_나눌_수_없으면_반영하지_않는다() {
        weeklyTestService.reflectClinicScore(classRoom, (short) 2026, (short) 8, (short) 1,
            null, null, hanul, (short) 0, (short) 0);
        // 전 문항이 내부지문이면 external_total = 0이라 ck_weekly_tests_shape에 걸린다
        weeklyTestService.reflectClinicScore(classRoom, (short) 2026, (short) 8, (short) 1,
            (short) 20, (short) 0, hanul, (short) 18, (short) 0);

        verify(weeklyTestRepository, never()).save(any());
        verify(weeklyTestScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("반영 상태 판정이 실제 반영 조건과 같다")
    void 반영_상태_판정() {
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 8, (short) 1))
            .willReturn(Optional.of(clinicHeader(700L, (short) 15, (short) 15)));

        assertThat(weeklyTestService.clinicReflectionOf(3L, (short) 2026, (short) 8, (short) 1,
            (short) 15, (short) 15)).isEqualTo(ClinicReflection.REFLECTED);
        assertThat(weeklyTestService.clinicReflectionOf(3L, (short) 2026, (short) 8, (short) 1,
            (short) 10, (short) 10)).isEqualTo(ClinicReflection.TOTAL_MISMATCH);
        assertThat(weeklyTestService.clinicReflectionOf(3L, (short) 2026, (short) 8, (short) 1,
            null, null)).isEqualTo(ClinicReflection.NO_INTERNAL_SPLIT);
    }

    // ---------- 삭제 경합 방어 (loadedAt) ----------

    @Test
    @DisplayName("화면을 연 뒤에 생긴 칸은 빈 칸으로 저장해도 지우지 않는다")
    void 화면을_연_뒤에_생긴_칸은_지키다() {
        // 선생님이 그리드를 열어 둔 사이 학생이 온라인 클리닉 테스트를 냈다.
        // 화면의 그 칸은 비어 있고, 그대로 저장하면 방금 반영된 성적이 조용히 사라진다
        OffsetDateTime loadedAt = OffsetDateTime.now().minusMinutes(10);
        WeeklyTest header = clinicHeader(700L, (short) 15, (short) 15);
        WeeklyTestScore autoFilled = WeeklyTestScore.create(header, hanul,
            null, (short) 12, (short) 9, null, false);
        ReflectionTestUtils.setField(autoFilled, "updatedAt", OffsetDateTime.now());

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(header));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(700L, 88L))
            .willReturn(Optional.of(autoFilled));

        weeklyTestService.save(new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            loadedAt,
            List.of(new WeeklyTestSaveRequest.TestInput(
                WeeklyTestType.CLINIC, null, (short) 15, (short) 15,
                List.of(new WeeklyTestSaveRequest.CellInput(
                    88L, null, null, null, null, false))))));

        verify(weeklyTestScoreRepository, never()).delete(any());
    }

    @Test
    @DisplayName("화면에 보이던 칸을 비우면 그대로 지운다 — 의도한 삭제는 막지 않는다")
    void 화면에_보이던_칸은_지운다() {
        OffsetDateTime loadedAt = OffsetDateTime.now();
        WeeklyTest header = clinicHeader(700L, (short) 15, (short) 15);
        WeeklyTestScore old = WeeklyTestScore.create(header, hanul,
            null, (short) 12, (short) 9, null, false);
        ReflectionTestUtils.setField(old, "updatedAt", OffsetDateTime.now().minusHours(1));

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(header));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(700L, 88L))
            .willReturn(Optional.of(old));

        weeklyTestService.save(new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            loadedAt,
            List.of(new WeeklyTestSaveRequest.TestInput(
                WeeklyTestType.CLINIC, null, (short) 15, (short) 15,
                List.of(new WeeklyTestSaveRequest.CellInput(
                    88L, null, null, null, null, false))))));

        verify(weeklyTestScoreRepository).delete(old);
    }

    @Test
    @DisplayName("화면을 연 뒤에 자동 반영된 클리닉이 있으면 헤더가 빈 채 저장해도 열을 지우지 않는다")
    void 화면을_연_뒤에_생긴_헤더는_지키다() {
        // 그리드를 열 때 그 주 클리닉 헤더가 없어 화면의 헤더 칸은 비어 있다. 그 사이
        // 학생이 온라인 클리닉 테스트를 내 헤더와 칸이 생겼다. 선생님은 단어 점수만 적고
        // 저장한다 — 비어 있던 클리닉 헤더가 삭제로 읽히면 CASCADE로 성적이 사라진다
        OffsetDateTime loadedAt = OffsetDateTime.now().minusMinutes(10);
        WeeklyTest header = clinicHeader(700L, (short) 15, (short) 15);
        WeeklyTestScore autoFilled = WeeklyTestScore.create(header, hanul,
            null, (short) 12, (short) 9, null, false);
        ReflectionTestUtils.setField(autoFilled, "updatedAt", OffsetDateTime.now());

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(header));
        given(weeklyTestScoreRepository.findByWeeklyTestIdIn(List.of(700L)))
            .willReturn(List.of(autoFilled));

        weeklyTestService.save(new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            loadedAt,
            List.of(new WeeklyTestSaveRequest.TestInput(
                WeeklyTestType.CLINIC, null, null, null,
                List.of(new WeeklyTestSaveRequest.CellInput(
                    88L, null, null, null, null, false))))));

        verify(weeklyTestRepository, never()).delete(any());
        verify(weeklyTestScoreRepository, never()).delete(any());
    }

    @Test
    @DisplayName("새 칸과 옛 칸이 섞여 있으면 헤더는 남기고 화면에 보였던 칸만 지운다")
    void 헤더를_비워도_옛_칸만_지운다() {
        OffsetDateTime loadedAt = OffsetDateTime.now().minusMinutes(10);
        WeeklyTest header = clinicHeader(700L, (short) 15, (short) 15);
        WeeklyTestScore autoFilled = WeeklyTestScore.create(header, hanul,
            null, (short) 12, (short) 9, null, false);
        ReflectionTestUtils.setField(autoFilled, "updatedAt", OffsetDateTime.now());
        WeeklyTestScore old = WeeklyTestScore.create(header, seojun,
            null, (short) 10, (short) 8, null, false);
        ReflectionTestUtils.setField(old, "updatedAt", OffsetDateTime.now().minusHours(1));

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(header));
        given(weeklyTestScoreRepository.findByWeeklyTestIdIn(List.of(700L)))
            .willReturn(List.of(autoFilled, old));

        weeklyTestService.save(new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            loadedAt,
            List.of(new WeeklyTestSaveRequest.TestInput(
                WeeklyTestType.CLINIC, null, null, null, List.of()))));

        verify(weeklyTestRepository, never()).delete(any());
        verify(weeklyTestScoreRepository).delete(old);
        verify(weeklyTestScoreRepository, never()).delete(autoFilled);
    }

    @Test
    @DisplayName("화면을 연 뒤에 바뀐 칸이 없으면 헤더를 비운 열은 통째로 지운다")
    void 새_칸이_없으면_헤더를_지운다() {
        OffsetDateTime loadedAt = OffsetDateTime.now();
        WeeklyTest header = clinicHeader(700L, (short) 15, (short) 15);
        WeeklyTestScore old = WeeklyTestScore.create(header, hanul,
            null, (short) 12, (short) 9, null, false);
        ReflectionTestUtils.setField(old, "updatedAt", OffsetDateTime.now().minusHours(1));

        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.CLINIC, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(header));
        given(weeklyTestScoreRepository.findByWeeklyTestIdIn(List.of(700L)))
            .willReturn(List.of(old));

        weeklyTestService.save(new WeeklyTestSaveRequest(3L, (short) 2026, (short) 5, (short) 3,
            loadedAt,
            List.of(new WeeklyTestSaveRequest.TestInput(
                WeeklyTestType.CLINIC, null, null, null, List.of()))));

        verify(weeklyTestRepository).delete(header);
    }

    // ---------- 푸시(#4) ----------

    private void givenWordColumn(WeeklyTest test, WeeklyTestScore existing) {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
            3L, WeeklyTestType.WORD, (short) 2026, (short) 5, (short) 3))
            .willReturn(Optional.of(test));
        given(weeklyTestScoreRepository.findByWeeklyTestIdAndStudentId(10L, 88L))
            .willReturn(Optional.ofNullable(existing));
    }

    private WeeklyTestSaveRequest wordCell(Short correct, TestResult result) {
        return saveRequest(new WeeklyTestSaveRequest.TestInput(
            WeeklyTestType.WORD, (short) 25, null, null,
            List.of(new WeeklyTestSaveRequest.CellInput(88L, correct, null, null, result,
                false))));
    }

    @Test
    @DisplayName("새로 적은 칸의 학생에게 주차 라벨이 붙은 성적 푸시가 나간다")
    void 새_칸은_성적_푸시() {
        givenWordColumn(wordTest(10L), null);

        weeklyTestService.save(wordCell((short) 23, TestResult.PASS));

        verify(eventPublisher).publishEvent(
            PushEvent.labeled(PushTopic.WEEKLY_SCORE, List.of(88L), "5월 3주"));
    }

    @Test
    @DisplayName("값이 바뀐 칸은 푸시, 그대로 다시 저장한 칸은 푸시가 없다")
    void 그대로면_푸시_없음() {
        WeeklyTest test = wordTest(10L);
        givenWordColumn(test, WeeklyTestScore.create(test, hanul, (short) 23, null, null,
            TestResult.PASS, false));

        weeklyTestService.save(wordCell((short) 23, TestResult.PASS));
        verify(eventPublisher, never()).publishEvent(any(Object.class));

        weeklyTestService.save(wordCell((short) 24, TestResult.PASS));
        verify(eventPublisher).publishEvent(
            PushEvent.labeled(PushTopic.WEEKLY_SCORE, List.of(88L), "5월 3주"));
    }

    @Test
    @DisplayName("칸을 지운 저장에는 푸시가 없다")
    void 지운_칸은_푸시_없음() {
        WeeklyTest test = wordTest(10L);
        givenWordColumn(test, WeeklyTestScore.create(test, hanul, (short) 23, null, null,
            TestResult.PASS, false));

        weeklyTestService.save(wordCell(null, null));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
