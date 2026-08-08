package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import com.njwenglish.entity.enums.HomeworkKind;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record HomeworkDetailResponse(Long homeworkId,
                                     Long classRoomId,
                                     String classRoomName,
                                     Long lessonId,
                                     LocalDate lessonDate,
                                     String title,
                                     String description,
                                     HomeworkKind kind,
                                     OffsetDateTime dueAt,
                                     HomeworkCountsResponse counts) {

    /** kind를 빼지 마라 — 수정 모달이 GRID 열에 마감 입력칸을 그려 PATCH가 400을 받는다. */
    public static HomeworkDetailResponse of(Homework homework, HomeworkCountsResponse counts) {
        return new HomeworkDetailResponse(
            homework.getId(), homework.getClassRoom().getId(), homework.getClassRoom().getName(),
            homework.getLesson() == null ? null : homework.getLesson().getId(),
            homework.getLesson() == null ? null : homework.getLesson().getLessonDate(),
            homework.getTitle(), homework.getDescription(), homework.getKind(),
            homework.getDueAt(), counts);
    }
}
