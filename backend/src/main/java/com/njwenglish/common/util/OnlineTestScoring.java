package com.njwenglish.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 온라인 테스트 채점. <b>비율로 한 번에 계산한다.</b>
 *
 * <p>{@code 100 / questionCount}를 문항마다 더하면 안 된다. 25문항은 4.0이라 딱 맞지만
 * 30문항은 3.333...이 되어 만점이 99.99가 된다. 반올림 오차가 누적되기 때문이다.
 *
 * <pre>
 * score = 획득 배점 합계 / 전체 배점 합계 × 100   (소수 둘째 자리 반올림)
 * </pre>
 *
 * <p>미체크(null)는 오답이고 감점은 없다. points가 null이면 전 문항 배점 1이다.
 */
public final class OnlineTestScoring {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    private OnlineTestScoring() {
    }

    public record Result(BigDecimal score, short correctCount) {
    }

    public static Result grade(Short[] correctChoices, Short[] chosenChoices, Short[] points) {
        long earned = 0;
        long total = 0;
        short correctCount = 0;

        for (int i = 0; i < correctChoices.length; i++) {
            long point = points == null || points[i] == null ? 1L : points[i];
            total += point;

            Short chosen = chosenChoices == null || i >= chosenChoices.length
                ? null : chosenChoices[i];
            if (chosen != null && chosen.equals(correctChoices[i])) {
                earned += point;
                correctCount++;
            }
        }

        // 전 문항 배점이 0이면 나눌 수 없다. 0점으로 둔다
        BigDecimal score = total == 0
            ? BigDecimal.ZERO.setScale(SCALE)
            : BigDecimal.valueOf(earned)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);

        return new Result(score, correctCount);
    }
}
