package com.njwenglish.dto.score;

import java.math.BigDecimal;

/**
 * 점수·등급·주차·메모만 고친다. 시험명·과목·날짜는 uq_scores 키라 바꾸려면 지우고 다시 넣는다.
 */
public record ScoreUpdateRequest(
    Long examScheduleId,
    BigDecimal rawScore,
    Short gradeLevel,
    Short year,
    Short month,
    Short week,
    String memo
) {
}
