package com.njwenglish.dto.score;

import java.time.LocalDate;

/**
 * 기간과 범위만 고친다. 반·연도·학기·시험종류는 UNIQUE 키라 옮기려면 지우고 다시 만든다.
 */
public record ExamScheduleUpdateRequest(
    LocalDate startDate,
    LocalDate endDate,
    String scopeNote
) {
}
