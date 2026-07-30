package com.njwenglish.dto.lesson;

import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * S-5 상세. <b>학생 전용이다.</b> 학부모용 수업 조회 API를 만들지 마라.
 *
 * <p>videoId·embedUrl은 저장하지 않고 videoUrl에서 파싱해 내려준다. 프론트가 URL 형식을
 * 판단하지 않게 하려는 것이다. videoId가 null이면 영상이 없는 수업이라 영역을 숨긴다.
 *
 * <p>attendanceStatus가 null이면 아직 확정 전이다. 출석이 아니라 "미확인"이다.
 */
public record StudentLessonDetailResponse(
    Long lessonId,
    LocalDate lessonDate,
    String title,
    String classRoomName,
    String videoId,
    String embedUrl,
    String content,
    String keyPoints,
    String nextPreview,
    Homework homework,
    AttendanceStatus attendanceStatus
) {
    /** 수업에 딸린 숙제가 없으면 null이다. */
    public record Homework(
        Long homeworkId,
        String title,
        String description,
        OffsetDateTime dueAt,
        SubmissionStatus submissionStatus
    ) {
    }
}
