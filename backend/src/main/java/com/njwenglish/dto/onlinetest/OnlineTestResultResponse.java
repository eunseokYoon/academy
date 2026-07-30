package com.njwenglish.dto.onlinetest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <b>제출 후에만</b> 내려간다. 정답과 해설지 URL이 여기 처음 등장한다.
 *
 * <p>반 평균은 넣지 않는다. 상대 지표는 학생·학부모에게 노출 금지다.
 */
public record OnlineTestResultResponse(
    Long testId,
    String title,
    BigDecimal score,
    Short correctCount,
    Short questionCount,
    OffsetDateTime submittedAt,
    String answerFileUrl,
    List<QuestionResult> results
) {
    /** chosen이 null이면 미체크다. 오답으로 처리되고 감점은 없다. */
    public record QuestionResult(int questionNo, Short chosen, Short correct, boolean isCorrect) {
    }
}
