package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.ScoreChartKind;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import java.time.LocalDate;
import com.njwenglish.support.Fixtures;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudentScoreQueryServiceTest {

    @Mock
    private WeeklyTestScoreRepository weeklyTestScoreRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private WeeklyTestRepository weeklyTestRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    private StudentScoreQueryService studentScoreQueryService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "목요일반", "ABCD12");
    private final Student hanul = Fixtures.student(88L, "김하늘");

    @BeforeEach
    void setUp() {
        studentScoreQueryService = new StudentScoreQueryService(weeklyTestScoreRepository,
            weeklyTestRepository, enrollmentRepository, studentAccessGuard);
    }

    private WeeklyTestScore wordCell(short week, short correct, TestResult result,
                                     boolean retestPassed) {
        WeeklyTest test = WeeklyTest.create(classRoom, WeeklyTestType.WORD,
            (short) 2026, (short) 5, week, (short) 25, null, null);
        return WeeklyTestScore.create(test, hanul, correct, null, null, result, retestPassed);
    }

    private WeeklyTestScore clinicCell(short week, short internal, short external) {
        WeeklyTest test = WeeklyTest.create(classRoom, WeeklyTestType.CLINIC,
            (short) 2026, (short) 5, week, null, (short) 10, (short) 5);
        return WeeklyTestScore.create(test, hanul, null, internal, external, null, false);
    }

    /** 리뷰는 Pass/Fail뿐이다 — ck_weekly_tests_shape가 total_count를 NULL로 못 박는다. */
    private WeeklyTestScore reviewCell(short week, TestResult result) {
        WeeklyTest test = WeeklyTest.create(classRoom, WeeklyTestType.REVIEW,
            (short) 2026, (short) 5, week, null, null, null);
        return WeeklyTestScore.create(test, hanul, null, null, null, result, false);
    }

    private WeeklyTestScore practiceCell(short week, short correct) {
        WeeklyTest test = WeeklyTest.create(classRoom, WeeklyTestType.PRACTICE,
            (short) 2026, (short) 5, week, (short) 45, null, null);
        return WeeklyTestScore.create(test, hanul, correct, null, null, null, false);
    }

    @Test
    @DisplayName("입력이 없는 종류는 섹션이 아예 안 뜬다")
    void 데이터가_없는_종류는_섹션이_없다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(wordCell((short) 3, (short) 22, TestResult.PASS, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).testType()).isEqualTo(WeeklyTestType.WORD);
    }

    @Test
    @DisplayName("섹션 순서는 단어 → 리뷰 → 실전모고 → 클리닉 고정이다")
    void 섹션_순서가_고정이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        // 저장 순서를 일부러 뒤집어 넣는다 — 서버가 종류 순서를 강제해야 한다
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(
                clinicCell((short) 3, (short) 8, (short) 4),
                wordCell((short) 3, (short) 22, TestResult.PASS, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.sections()).extracting(StudentScoreResponse.Section::testType)
            .containsExactly(WeeklyTestType.WORD, WeeklyTestType.CLINIC);
    }

    @Test
    @DisplayName("FAIL이고 재시험 미체크면 재시험 예정으로 잡힌다")
    void 미통과는_재시험_예정이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(wordCell((short) 2, (short) 18, TestResult.FAIL, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.retestScheduled()).hasSize(1);
        assertThat(response.retestScheduled().get(0).label()).isEqualTo("5월 2주 단어 테스트");
        assertThat(response.sections().get(0).items().get(0).retestScheduled()).isTrue();
    }

    @Test
    @DisplayName("재시험 통과 체크가 되면 요약 박스에서 빠진다")
    void 재시험_통과는_요약에서_빠진다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(wordCell((short) 2, (short) 22, TestResult.FAIL, true)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.retestScheduled()).isEmpty();
        assertThat(response.sections().get(0).items().get(0).retestPassed()).isTrue();
        assertThat(response.sections().get(0).items().get(0).retestScheduled()).isFalse();
    }

    /**
     * 환산 점수·정답률은 전 화면에서 없앤다(2026-09-10 선생님 회의).
     * 필드를 지우지 않고 null을 내리는 이유는 배포 순서가 backend → web이라,
     * 필드가 사라지면 옛 화면에서 accuracy !== null이 참이 되어 "undefined%"가 그려진다.
     */
    @Test
    @DisplayName("정답률은 항상 null이다 - 맞힌 개수와 전체 문항 수만 내려간다")
    void 정답률은_항상_null이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(wordCell((short) 3, (short) 22, TestResult.PASS, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        var item = response.sections().get(0).items().get(0);
        assertThat(item.accuracy()).isNull();
        // 원본은 그대로 내려간다. 화면이 "22/25"를 그린다
        assertThat(item.correctCount()).isEqualTo((short) 22);
        assertThat(item.totalCount()).isEqualTo((short) 25);
    }

    @Test
    @DisplayName("클리닉은 정답률도 그래프도 없다 — 값이 둘이라 한 선으로 못 그린다")
    void 클리닉은_그래프가_없다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(clinicCell((short) 3, (short) 8, (short) 4)));

        StudentScoreResponse.Section section =
            studentScoreQueryService.forStudent(88L).sections().get(0);

        assertThat(section.chart()).isFalse();
        assertThat(section.items().get(0).accuracy()).isNull();
        assertThat(section.items().get(0).internalCorrect()).isEqualTo((short) 8);
        assertThat(section.items().get(0).internalTotal()).isEqualTo((short) 10);
    }

    @Test
    @DisplayName("items는 리포지토리가 준 오름차순 그대로다 — 다시 정렬하지 않는다")
    void items는_오름차순_그대로다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(
                wordCell((short) 1, (short) 23, TestResult.PASS, false),
                wordCell((short) 2, (short) 18, TestResult.FAIL, false),
                wordCell((short) 3, (short) 22, TestResult.PASS, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.sections().get(0).items())
            .extracting(StudentScoreResponse.Item::week)
            .containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    @DisplayName("재시험 예정 요약은 최신 주차부터 내림차순이다")
    void 재시험_요약은_최신순이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(
                wordCell((short) 1, (short) 15, TestResult.FAIL, false),
                wordCell((short) 4, (short) 17, TestResult.FAIL, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.retestScheduled())
            .extracting(StudentScoreResponse.RetestNotice::weekLabel)
            .containsExactly("5월 4주", "5월 1주");
    }

    /** 화면에서 testType으로 분기하면 S-7과 P-6이 갈라진다. 서버가 정한다. */
    @Test
    @DisplayName("종류마다 그래프 종류를 서버가 정한다")
    void 종류마다_그래프_종류를_정한다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(
                wordCell((short) 3, (short) 22, TestResult.PASS, false),
                reviewCell((short) 3, TestResult.PASS),
                practiceCell((short) 3, (short) 38),
                clinicCell((short) 3, (short) 8, (short) 4)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        Map<WeeklyTestType, ScoreChartKind> kinds = response.sections().stream()
            .collect(Collectors.toMap(
                StudentScoreResponse.Section::testType,
                StudentScoreResponse.Section::chartKind));

        assertThat(kinds).containsEntry(WeeklyTestType.WORD, ScoreChartKind.NONE);
        assertThat(kinds).containsEntry(WeeklyTestType.REVIEW, ScoreChartKind.NONE);
        assertThat(kinds).containsEntry(WeeklyTestType.PRACTICE, ScoreChartKind.BAR);
        assertThat(kinds).containsEntry(WeeklyTestType.CLINIC, ScoreChartKind.SPLIT_BAR);
    }

    private WeeklyTest wordHeader(short year, short month, short week) {
        return WeeklyTest.create(classRoom, WeeklyTestType.WORD, year, month, week,
            (short) 25, null, null);
    }

    @Test
    @DisplayName("반이 본 시험의 칸이 비면 그 주에 미응시 줄이 순서대로 끼어든다")
    void 미응시_줄이_끼어든다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L)).willReturn(List.of(
            wordCell((short) 1, (short) 20, TestResult.PASS, false),
            wordCell((short) 3, (short) 22, TestResult.PASS, false)));
        given(enrollmentRepository.findByStudentId(88L)).willReturn(List.of(
            Enrollment.create(hanul, classRoom, LocalDate.of(2026, 3, 2))));
        given(weeklyTestRepository.findTakenByOthersOnly(List.of(3L), 88L))
            .willReturn(List.of(wordHeader((short) 2026, (short) 5, (short) 2)));

        StudentScoreResponse.Section word = studentScoreQueryService.forStudent(88L)
            .sections().get(0);

        assertThat(word.items()).extracting(StudentScoreResponse.Item::week)
            .containsExactly((short) 1, (short) 2, (short) 3);
        StudentScoreResponse.Item absent = word.items().get(1);
        assertThat(absent.absent()).isTrue();
        // 0점이 아니다 — 값 칸은 비어 있다
        assertThat(absent.correctCount()).isNull();
        assertThat(absent.result()).isNull();
        assertThat(word.items().get(0).absent()).isFalse();
    }

    @Test
    @DisplayName("기록이 하나도 없는 종류도 미응시만 있으면 섹션이 생긴다")
    void 미응시만_있어도_섹션이_생긴다() {
        given(studentAccessGuard.requireSelf()).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L)).willReturn(List.of());
        given(enrollmentRepository.findByStudentId(88L)).willReturn(List.of(
            Enrollment.create(hanul, classRoom, LocalDate.of(2026, 3, 2))));
        given(weeklyTestRepository.findTakenByOthersOnly(List.of(3L), 88L))
            .willReturn(List.of(wordHeader((short) 2026, (short) 5, (short) 2)));

        StudentScoreResponse response = studentScoreQueryService.forMe();

        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).items().get(0).absent()).isTrue();
    }

    @Test
    @DisplayName("이번 주·입반 전 주는 미응시가 아니다 — 선생님이 아직 적는 중일 수 있다")
    void 이번주와_입반전은_미응시가_아니다() {
        LocalDate today = LocalDate.now();
        short thisWeek = com.njwenglish.common.util.MonthWeeks.of(today);
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L)).willReturn(List.of());
        // 5월 1주는 입반(5월 20일) 전이다
        given(enrollmentRepository.findByStudentId(88L)).willReturn(List.of(
            Enrollment.create(hanul, classRoom, LocalDate.of(2026, 5, 20))));
        given(weeklyTestRepository.findTakenByOthersOnly(List.of(3L), 88L)).willReturn(List.of(
            wordHeader((short) today.getYear(), (short) today.getMonthValue(), thisWeek),
            wordHeader((short) 2026, (short) 5, (short) 1)));

        assertThat(studentScoreQueryService.forStudent(88L).sections()).isEmpty();
    }
}
