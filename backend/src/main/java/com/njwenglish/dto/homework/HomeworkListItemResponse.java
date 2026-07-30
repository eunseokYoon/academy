package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import java.time.OffsetDateTime;

/** lessonId가 null이면 캘린더 색띠 계산에서 빠진다. 목록에서 눈에 띄어야 연결을 유도할 수 있다. */
public record HomeworkListItemResponse(Long homeworkId,
                                       Long classRoomId,
                                       String classRoomName,
                                       Long lessonId,
                                       String title,
                                       OffsetDateTime dueAt,
                                       HomeworkCountsResponse counts) {

    public static HomeworkListItemResponse of(Homework homework, HomeworkCountsResponse counts) {
        return new HomeworkListItemResponse(
            homework.getId(), homework.getClassRoom().getId(), homework.getClassRoom().getName(),
            homework.getLesson() == null ? null : homework.getLesson().getId(),
            homework.getTitle(), homework.getDueAt(), counts);
    }
}
