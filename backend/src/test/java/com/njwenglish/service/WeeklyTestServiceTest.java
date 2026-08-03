package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.njwenglish.dto.weeklytest.WeeklyTestGridResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
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
    private StudentRepository studentRepository;

    private WeeklyTestService weeklyTestService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "목요일반", "ABCD12");
    private final Student hanul = Fixtures.student(88L, "김하늘");
    private final Student seojun = Fixtures.student(91L, "이서준");

    @BeforeEach
    void setUp() {
        weeklyTestService = new WeeklyTestService(weeklyTestRepository, weeklyTestScoreRepository,
            enrollmentRepository, classRoomRepository, studentRepository);
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
}
