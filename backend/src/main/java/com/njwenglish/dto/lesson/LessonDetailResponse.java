package com.njwenglish.dto.lesson;

import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * videos는 원본 url과 파싱된 embedUrl을 함께 담는다 — 선생님 화면은 수정용으로 원본이,
 * 미리보기용으로 embedUrl이 필요하다. 학생·학부모용은 {@link LessonReportResponse}다.
 */
public record LessonDetailResponse(
    Long lessonId,
    Long classRoomId,
    String classRoomName,
    LocalDate lessonDate,
    Short year,
    Short month,
    Short week,
    String title,
    List<LessonVideoEditResponse> videos,
    String content,
    String keyPoints,
    String homeworkNote,
    String clinicNote,
    LessonAttendanceStatus attendanceStatus,
    OffsetDateTime publishedAt
) {
    public static LessonDetailResponse from(Lesson lesson) {
        return new LessonDetailResponse(
            lesson.getId(), lesson.getClassRoom().getId(), lesson.getClassRoom().getName(),
            lesson.getLessonDate(), lesson.getYear(), lesson.getMonth(), lesson.getWeek(),
            lesson.getTitle(),
            lesson.getVideos().stream().map(LessonVideoEditResponse::from).toList(),
            lesson.getContent(), lesson.getKeyPoints(), lesson.getHomeworkNote(), lesson.getClinicNote(),
            lesson.getAttendanceStatus(), lesson.getPublishedAt());
    }
}
