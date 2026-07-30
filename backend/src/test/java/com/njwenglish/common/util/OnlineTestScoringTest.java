package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnlineTestScoringTest {

    private static Short[] answers(int count, int value) {
        Short[] array = new Short[count];
        Arrays.fill(array, (short) value);
        return array;
    }

    @Test
    @DisplayName("30문항 만점이 정확히 100.00이다 — 100/문항수를 더하면 99.99가 된다")
    void 삼십문항_만점은_정확히_100점이다() {
        Short[] correct = answers(30, 3);

        OnlineTestScoring.Result result = OnlineTestScoring.grade(correct, correct, null);

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.correctCount()).isEqualTo((short) 30);
    }

    @Test
    @DisplayName("균등 배점은 비율로 계산한다 — 25문항 중 23개면 92.00")
    void 균등배점은_비율로_계산한다() {
        Short[] correct = answers(25, 1);
        Short[] chosen = answers(25, 1);
        chosen[0] = 2;
        chosen[1] = 2;

        OnlineTestScoring.Result result = OnlineTestScoring.grade(correct, chosen, null);

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(result.correctCount()).isEqualTo((short) 23);
    }

    @Test
    @DisplayName("미체크(null)는 오답이고 감점은 없다")
    void 미체크는_오답이고_감점은_없다() {
        Short[] correct = answers(4, 2);
        Short[] chosen = {2, null, null, 2};

        OnlineTestScoring.Result result = OnlineTestScoring.grade(correct, chosen, null);

        assertThat(result.correctCount()).isEqualTo((short) 2);
        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("배점 배열이 있으면 문항별 배점으로 계산한다")
    void 문항별_배점을_반영한다() {
        Short[] correct = {1, 2, 3};
        Short[] chosen = {1, 9, 3};
        Short[] points = {1, 8, 1};

        // 맞힌 배점 2 / 전체 배점 10
        OnlineTestScoring.Result result = OnlineTestScoring.grade(correct, chosen, points);

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.correctCount()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("나누어떨어지지 않는 배점도 소수 둘째 자리에서 반올림한다")
    void 소수_둘째자리에서_반올림한다() {
        Short[] correct = answers(3, 1);
        Short[] chosen = {1, 1, 2};

        // 2/3 × 100 = 66.666...
        assertThat(OnlineTestScoring.grade(correct, chosen, null).score())
            .isEqualByComparingTo(new BigDecimal("66.67"));
    }

    @Test
    @DisplayName("아무 답도 저장하지 않은 채 제출해도 0점으로 채점된다")
    void 답이_비어도_0점으로_채점된다() {
        Short[] correct = answers(5, 1);

        OnlineTestScoring.Result result = OnlineTestScoring.grade(correct, new Short[5], null);

        assertThat(result.score()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(result.correctCount()).isZero();
    }
}
