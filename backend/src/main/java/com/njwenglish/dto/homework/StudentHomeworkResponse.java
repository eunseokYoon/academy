package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import com.njwenglish.entity.enums.HomeworkKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** S-4의 숙제 블록. 학생은 숙제 내용(description)까지 본다. 학부모는 보지 않는다. */
public record StudentHomeworkResponse(Long id,
                                      String title,
                                      String description,
                                      HomeworkKind kind,
                                      LocalDate lessonDate,
                                      OffsetDateTime dueAt,
                                      String classRoomName) {

    public static StudentHomeworkResponse from(Homework homework) {
        return new StudentHomeworkResponse(
            homework.getId(), homework.getTitle(), homework.getDescription(),
            homework.getKind(),
            homework.getLesson() == null ? null : homework.getLesson().getLessonDate(),
            homework.getDueAt(), homework.getClassRoom().getName());
    }
}
