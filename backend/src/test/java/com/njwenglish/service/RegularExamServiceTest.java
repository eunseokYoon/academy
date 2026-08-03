package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.regularexam.RegularExamGridResponse;
import com.njwenglish.dto.regularexam.RegularExamSaveRequest;
import com.njwenglish.entity.RegularExamScore;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.RegularExamSlot;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.RegularExamScoreRepository;
import com.njwenglish.support.Fixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegularExamServiceTest {

    @Mock
    private RegularExamScoreRepository regularExamScoreRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private RegularExamService regularExamService;

    private final Student hanul = Fixtures.student(88L, "김하늘");

    @BeforeEach
    void setUp() {
        regularExamService = new RegularExamService(regularExamScoreRepository,
            enrollmentRepository, studentAccessGuard);
    }

    private RegularExamSaveRequest saveRequest(RegularExamSlot slot, BigDecimal rawScore) {
        return new RegularExamSaveRequest(3L, (short) 2026,
            List.of(new RegularExamSaveRequest.Item(88L, slot, rawScore)));
    }

    @Test
    @DisplayName("그 해 점수가 있는 학생은 퇴원했어도 명단에 나온다")
    void 퇴원생도_점수가_있으면_명단에_나온다() {
        Student seojun = Fixtures.student(91L, "이서준");
        given(enrollmentRepository.findActiveStudents(anyLong(), any(LocalDate.class)))
            .willReturn(List.of(hanul));
        given(regularExamScoreRepository.findStudentsWithScores(3L, (short) 2026))
            .willReturn(List.of(seojun));
        given(regularExamScoreRepository.findByStudentIdInAndYear(List.of(88L, 91L),
            (short) 2026)).willReturn(List.of());

        RegularExamGridResponse grid = regularExamService.grid(3L, (short) 2026);

        assertThat(grid.students()).extracting(RegularExamGridResponse.StudentRow::name)
            .containsExactly("김하늘", "이서준");
        assertThat(grid.students()).extracting(RegularExamGridResponse.StudentRow::enrolled)
            .containsExactly(true, false);
    }

    @Test
    @DisplayName("rawScore가 null이면 행을 지운다")
    void 점수를_비우면_행이_삭제된다() {
        RegularExamScore existing = RegularExamScore.create(hanul, (short) 2026,
            RegularExamSlot.S1_MIDTERM, new BigDecimal("96"));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(regularExamScoreRepository.findByStudentIdAndYearAndExamSlot(88L, (short) 2026,
            RegularExamSlot.S1_MIDTERM)).willReturn(Optional.of(existing));

        regularExamService.save(saveRequest(RegularExamSlot.S1_MIDTERM, null));

        verify(regularExamScoreRepository).delete(existing);
    }

    @Test
    @DisplayName("같은 칸을 다시 저장하면 덮어쓴다 — 409를 던지지 않는다")
    void 같은_칸을_다시_저장하면_덮어쓴다() {
        RegularExamScore existing = RegularExamScore.create(hanul, (short) 2026,
            RegularExamSlot.S1_MIDTERM, new BigDecimal("96"));
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(regularExamScoreRepository.findByStudentIdAndYearAndExamSlot(88L, (short) 2026,
            RegularExamSlot.S1_MIDTERM)).willReturn(Optional.of(existing));

        regularExamService.save(saveRequest(RegularExamSlot.S1_MIDTERM, new BigDecimal("88")));

        assertThat(existing.getRawScore()).isEqualByComparingTo("88");
    }

    @Test
    @DisplayName("새 점수는 requireAccessible이 돌려준 학생으로 만든다 — 가드를 건너뛰지 않는다")
    void 새_점수도_가드를_거친다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(regularExamScoreRepository.findByStudentIdAndYearAndExamSlot(88L, (short) 2026,
            RegularExamSlot.MOCK_MAR)).willReturn(Optional.empty());

        regularExamService.save(saveRequest(RegularExamSlot.MOCK_MAR, new BigDecimal("72")));

        verify(studentAccessGuard).requireAccessible(88L);
        verify(regularExamScoreRepository).save(any(RegularExamScore.class));
    }

    @Test
    @DisplayName("삭제 경로도 가드를 거친다 — 남의 학생 행을 지우지 못한다")
    void 삭제_경로도_가드를_거친다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(hanul);
        given(regularExamScoreRepository.findByStudentIdAndYearAndExamSlot(88L, (short) 2026,
            RegularExamSlot.S1_FINAL)).willReturn(Optional.empty());

        regularExamService.save(saveRequest(RegularExamSlot.S1_FINAL, null));

        verify(studentAccessGuard).requireAccessible(88L);
    }
}
