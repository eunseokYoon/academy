package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 내부·외부 집계와 틀린 문항 번호는 순수 계산이라 리포지토리 없이 검증한다.
 *
 * <p>온라인 테스트는 클리닉 테스트의 온라인 대체본이다. 선생님이 이 집계를 보고
 * 성적 기입 탭의 클리닉 칸(내부·외부 맞힌 수)에 옮겨 적는다.
 */
class OnlineTestResultAggregationTest {

    @Test
    @DisplayName("앞 N문항이 내부지문으로 집계된다")
    void 내부지문은_앞_N문항이다() {
        Short[] correct = {1, 2, 3, 1, 2};
        Short[] chosen = {1, 2, 9, 1, 9};

        assertThat(OnlineTestService.countCorrect(correct, chosen, 0, 3)).isEqualTo((short) 2);
        assertThat(OnlineTestService.countCorrect(correct, chosen, 3, 5)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("미체크(null)는 오답이다 — 감점은 없다")
    void 미체크는_오답이다() {
        Short[] correct = {1, 2, 3};
        Short[] chosen = {1, null, 3};

        assertThat(OnlineTestService.wrongQuestionNos(correct, chosen)).isEqualTo(List.of(2));
        assertThat(OnlineTestService.countCorrect(correct, chosen, 0, 3)).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("틀린 문항 번호는 1부터 센다 — 선생님이 시험지에서 찾는 번호와 같아야 한다")
    void 문항_번호는_1부터다() {
        Short[] correct = {1, 2, 3, 4};
        Short[] chosen = {9, 2, 9, 4};

        assertThat(OnlineTestService.wrongQuestionNos(correct, chosen)).isEqualTo(List.of(1, 3));
    }

    @Test
    @DisplayName("답안 배열이 정답 배열보다 짧아도 터지지 않는다 — 뒷문항은 미체크로 본다")
    void 답안이_짧아도_안_터진다() {
        Short[] correct = {1, 2, 3};
        Short[] chosen = {1};

        assertThat(OnlineTestService.wrongQuestionNos(correct, chosen)).isEqualTo(List.of(2, 3));
        assertThat(OnlineTestService.countCorrect(correct, chosen, 0, 3)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("내부지문 문항 수가 0이면 전부 외부지문이다")
    void 내부가_0이면_전부_외부다() {
        Short[] correct = {1, 2, 3};
        Short[] chosen = {1, 2, 3};

        assertThat(OnlineTestService.countCorrect(correct, chosen, 0, 0)).isEqualTo((short) 0);
        assertThat(OnlineTestService.countCorrect(correct, chosen, 0, 3)).isEqualTo((short) 3);
    }
}
