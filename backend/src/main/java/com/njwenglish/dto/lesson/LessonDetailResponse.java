package com.njwenglish.dto.lesson;

import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** videoId·embedUrl은 저장하지 않고 videoUrl에서 파싱해 내려준다. 프론트가 iframe에 쓴다. */
public record LessonDetailResponse(
    Long lessonId,
    Long classRoomId,
    String classRoomName,
    LocalDate lessonDate,
    Short year,
    Short month,
    Short week,
    String title,
    String videoUrl,
    String videoId,
    String embedUrl,
    String content,
    String keyPoints,
    String nextPreview,
    LessonAttendanceStatus attendanceStatus,
    OffsetDateTime publishedAt
) {
    public static LessonDetailResponse from(Lesson lesson) {
        String videoId = YoutubeUrls.videoId(lesson.getVideoUrl());
        return new LessonDetailResponse(
            lesson.getId(), lesson.getClassRoom().getId(), lesson.getClassRoom().getName(),
            lesson.getLessonDate(), lesson.getYear(), lesson.getMonth(), lesson.getWeek(),
            lesson.getTitle(), lesson.getVideoUrl(), videoId,
            YoutubeUrls.embedUrlOf(lesson.getVideoUrl()),
            lesson.getContent(), lesson.getKeyPoints(), lesson.getNextPreview(),
            lesson.getAttendanceStatus(), lesson.getPublishedAt());
    }
}
