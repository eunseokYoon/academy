package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import com.njwenglish.support.Fixtures;
import java.util.List;
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

    private StudentScoreQueryService studentScoreQueryService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "목요일반", "ABCD12");
    private final Student hanul = Fixtures.student(88L, "김하늘");

    @BeforeEach
    void setUp() {
        studentScoreQueryService = new StudentScoreQueryService(weeklyTestScoreRepository,
            studentAccessGuard);
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

    @Test
    @DisplayName("정답률은 서버가 계산한다 — 22/25는 88.0")
    void 정답률은_서버가_계산한다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(weeklyTestScoreRepository.findByStudentOrderedByWeek(88L))
            .willReturn(List.of(wordCell((short) 3, (short) 22, TestResult.PASS, false)));

        StudentScoreResponse response = studentScoreQueryService.forStudent(88L);

        assertThat(response.sections().get(0).items().get(0).accuracy())
            .isEqualByComparingTo("88.0");
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
}
