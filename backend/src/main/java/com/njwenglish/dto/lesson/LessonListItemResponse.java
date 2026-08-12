package com.njwenglish.dto.lesson;

import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;

/**
 * 선생님이 "무엇을 안 했는지" 찾는 화면이다. 세 상태를 한눈에 보여준다 —
 * 내용 미작성(contentWritten), 미공개(published), 출석 미확정(attendanceStatus).
 */
public record LessonListItemResponse(
    Long lessonId,
    Long classRoomId,
    String classRoomName,
    LocalDate lessonDate,
    Short year,
    Short month,
    Short week,
    String title,
    boolean contentWritten,
    boolean published,
    LessonAttendanceStatus attendanceStatus
) {
    public static LessonListItemResponse from(Lesson lesson) {
        return new LessonListItemResponse(
            lesson.getId(), lesson.getClassRoom().getId(), lesson.getClassRoom().getName(),
            lesson.getLessonDate(), lesson.getYear(), lesson.getMonth(), lesson.getWeek(),
            lesson.getTitle(),
            lesson.getContent() != null && !lesson.getContent().isBlank(),
            lesson.isPublished(), lesson.getAttendanceStatus());
    }
}
