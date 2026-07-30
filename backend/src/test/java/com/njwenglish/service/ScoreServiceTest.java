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
import com.njwenglish.dto.score.ScoreBulkCreateRequest;
import com.njwenglish.dto.score.ScoreBulkCreateResponse;
import com.njwenglish.dto.score.ScoreChartResponse;
import com.njwenglish.dto.score.ScoreCreateRequest;
import com.njwenglish.entity.Score;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.repository.ExamScheduleRepository;
import com.njwenglish.repository.ScoreRepository;
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
class ScoreServiceTest {

    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 5, 13);

    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private ExamScheduleRepository examScheduleRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private ScoreService scoreService;

    private final Student student = Fixtures.student(88L, "서동환");

    @BeforeEach
    void setUp() {
        scoreService = new ScoreService(scoreRepository, examScheduleRepository,
            studentAccessGuard);
    }

    private ScoreCreateRequest word(BigDecimal rawScore, Short gradeLevel) {
        return new ScoreCreateRequest(ScoreType.WORD, null, "5월 3주차 단어시험", "영어",
            rawScore, gradeLevel, EXAM_DATE, (short) 2026, (short) 5, (short) 3, null);
    }

    private Score wordScore(short month, short week, String rawScore) {
        return Score.create(student, ScoreType.WORD, null,
            month + "월 " + week + "주차 단어시험", "영어", new BigDecimal(rawScore), null,
            EXAM_DATE, (short) 2026, month, week, null);
    }

    @Test
    @DisplayName("WORD는 100점 만점 환산값이 필수다 — 점수가 없으면 400")
    void 단어시험은_점수가_필수다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

        assertThatThrownBy(() -> scoreService.create(88L, word(null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("WORD 점수가 100을 넘으면 400이다 — 원점수를 그대로 보낸 경우를 잡는다")
    void 단어시험_점수가_100을_넘으면_400이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

        assertThatThrownBy(() -> scoreService.create(88L, word(new BigDecimal("120"), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("WORD에는 등급이 없다 — gradeLevel을 보내면 400")
    void 단어시험에_등급을_넣으면_400이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

        assertThatThrownBy(() -> scoreService.create(88L, word(new BigDecimal("88"), (short) 1)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("내신이 아닌 성적에 시험 일정을 붙이면 400이다")
    void 내신이_아니면_시험일정을_붙일_수_없다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);

        assertThatThrownBy(() -> scoreService.create(88L, new ScoreCreateRequest(
            ScoreType.MOCK, 12L, "3월 학평", "영어", null, (short) 1,
            EXAM_DATE, (short) 2026, (short) 3, (short) 4, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("선생님이 고른 주차를 그대로 저장한다 — examDate에서 계산하지 않는다")
    void 주차를_그대로_저장한다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
        given(scoreRepository.findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
            anyLong(), any(), any(), any(), any())).willReturn(Optional.empty());
        given(scoreRepository.save(any())).willAnswer(i -> i.getArgument(0));

        // 5월 13일은 달력상 2주차지만 선생님이 고른 3을 저장한다
        assertThat(scoreService.create(88L, word(new BigDecimal("88"), null)).week())
            .isEqualTo((short) 3);
    }

    @Test
    @DisplayName("같은 시험을 다시 저장하면 행이 늘지 않고 갱신된다 — 저장 버튼을 두 번 눌러도 안전하다")
    void 같은_시험은_갱신된다() {
        Score existing = wordScore((short) 5, (short) 3, "80");
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
        given(scoreRepository.findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
            88L, ScoreType.WORD, "영어", "5월 3주차 단어시험", EXAM_DATE))
            .willReturn(Optional.of(existing));

        scoreService.create(88L, word(new BigDecimal("88"), null));

        verify(scoreRepository, never()).save(any());
        assertThat(existing.getRawScore()).isEqualByComparingTo(new BigDecimal("88"));
    }

    @Test
    @DisplayName("일괄 저장은 새 행과 갱신 행을 구분해 돌려준다")
    void 일괄_저장은_created와_updated를_구분한다() {
        Student other = Fixtures.student(91L, "김하늘");
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
        given(studentAccessGuard.requireAccessible(91L)).willReturn(other);
        given(scoreRepository.findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
            88L, ScoreType.WORD, "영어", "5월 3주차 단어시험", EXAM_DATE))
            .willReturn(Optional.empty());
        given(scoreRepository.findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
            91L, ScoreType.WORD, "영어", "5월 3주차 단어시험", EXAM_DATE))
            .willReturn(Optional.of(wordScore((short) 5, (short) 3, "70")));
        given(scoreRepository.save(any())).willAnswer(i -> i.getArgument(0));

        ScoreBulkCreateResponse response = scoreService.bulkCreate(new ScoreBulkCreateRequest(
            ScoreType.WORD, null, "5월 3주차 단어시험", "영어", EXAM_DATE,
            (short) 2026, (short) 5, (short) 3,
            List.of(new ScoreBulkCreateRequest.Item(88L, new BigDecimal("88"), null, null),
                new ScoreBulkCreateRequest.Item(91L, new BigDecimal("92"), null, null))));

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("주차별 그래프의 label은 서버가 만든다 — 프론트가 조립하면 표기가 갈린다")
    void 그래프_label은_서버가_만든다() {
        given(studentAccessGuard.requireSelf()).willReturn(student);
        given(scoreRepository.findWordSeries(88L)).willReturn(List.of(
            wordScore((short) 5, (short) 3, "88"),
            wordScore((short) 5, (short) 4, "92")));
        given(scoreRepository.findByType(88L, ScoreType.INTERNAL)).willReturn(List.of());
        given(scoreRepository.findByType(88L, ScoreType.MOCK)).willReturn(List.of());

        ScoreChartResponse chart = scoreService.myScores(null);

        assertThat(chart.word().unit()).isEqualTo("주차");
        assertThat(chart.word().points()).extracting(ScoreChartResponse.Point::label)
            .containsExactly("5월 3주", "5월 4주");
    }

    @Test
    @DisplayName("scoreType을 지정하면 그 종류만 채워진다")
    void scoreType으로_구간을_좁힌다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(student);
        given(scoreRepository.findWordSeries(88L))
            .willReturn(List.of(wordScore((short) 5, (short) 3, "88")));

        ScoreChartResponse chart = scoreService.childScores(88L, ScoreType.WORD);

        assertThat(chart.word().points()).hasSize(1);
        assertThat(chart.internal()).isEmpty();
        assertThat(chart.mock()).isEmpty();
        verify(scoreRepository, never()).findByType(anyLong(), any());
    }
}
