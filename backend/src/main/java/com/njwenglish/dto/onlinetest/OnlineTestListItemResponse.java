package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.enums.ScoreType;
import java.time.OffsetDateTime;

/** T-14 목록. 선생님 화면이라 정답은 빼고 상태만 보여 준다. */
public record OnlineTestListItemResponse(
    Long testId,
    String title,
    Long classRoomId,
    String classRoomName,
    Short questionCount,
    Short year,
    Short month,
    Short week,
    ScoreType scoreType,
    String subject,
    boolean published,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt
) {
    public static OnlineTestListItemResponse from(OnlineTest test) {
        return new OnlineTestListItemResponse(
            test.getId(), test.getTitle(),
            test.getClassRoom().getId(), test.getClassRoom().getName(),
            test.getQuestionCount(), test.getYear(), test.getMonth(), test.getWeek(),
            test.getScoreType(), test.getSubject(), test.isPublished(),
            test.getOpensAt(), test.getClosesAt());
    }
}
