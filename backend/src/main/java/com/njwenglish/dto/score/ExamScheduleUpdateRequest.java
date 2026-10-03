package com.njwenglish.dto.score;

import java.time.LocalDate;

/**
 * 기간과 범위만 고친다. 반·연도·학기·시험종류는 UNIQUE 키라 옮기려면 지우고 다시 만든다.
 *
 * <p>각 필드가 null 이면 그대로 둔다. {@code scopeNote} 만 예외로 <b>빈 문자열이면 범위를 지운다</b>.
 */
public record ExamScheduleUpdateRequest(
    LocalDate startDate,
    LocalDate endDate,
    String scopeNote
) {
}
