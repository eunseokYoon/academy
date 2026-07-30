package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record HomeworkDetailResponse(Long homeworkId,
                                     Long classRoomId,
                                     String classRoomName,
                                     Long lessonId,
                                     LocalDate lessonDate,
                                     String title,
                                     String description,
                                     OffsetDateTime dueAt,
                                     HomeworkCountsResponse counts) {

    public static HomeworkDetailResponse of(Homework homework, HomeworkCountsResponse counts) {
        return new HomeworkDetailResponse(
            homework.getId(), homework.getClassRoom().getId(), homework.getClassRoom().getName(),
            homework.getLesson() == null ? null : homework.getLesson().getId(),
            homework.getLesson() == null ? null : homework.getLesson().getLessonDate(),
            homework.getTitle(), homework.getDescription(), homework.getDueAt(), counts);
    }
}
