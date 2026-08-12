package com.njwenglish.dto.home;

import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * S-1 홈의 "지금 할 숙제". <b>이 목록이 화면에서 가장 커야 한다</b> —
 * 학생이 서비스를 여는 이유가 "뭘 해야 하는지" 확인하는 것이다.
 *
 * <p>remainingMinutes가 음수면 마감이 지난 것이다. 그래도 목록에서 빼지 마라.
 */
public record HomeHomeworkResponse(
    Long homeworkId,
    String title,
    OffsetDateTime dueAt,
    SubmissionStatus status,
    long remainingMinutes
) {
    public static HomeHomeworkResponse from(Submission submission, OffsetDateTime now) {
        Homework homework = submission.getHomework();
        return new HomeHomeworkResponse(
            homework.getId(), homework.getTitle(), homework.getDueAt(), submission.getStatus(),
            ChronoUnit.MINUTES.between(now, homework.getDueAt()));
    }
}
