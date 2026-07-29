package com.njwenglish.dto.lesson;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 반의 dayOfWeek를 기준으로 한 학기 수업일을 미리 만든다.
 * 20회를 매번 손으로 만들게 하면 실제로 안 쓴다.
 */
public record LessonBulkCreateRequest(
    @NotNull Long classRoomId,
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    List<LocalDate> skipDates
) {
}
