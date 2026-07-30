package com.njwenglish.dto.lesson;

import java.time.LocalDate;

/**
 * S-5 목록. 선생님용 DTO와 분리한 이유는 videoUrl·publishedAt처럼 학생에게 필요 없는 값을
 * 빼기 위해서다. hasVideo만 있으면 프론트가 영상 영역을 보일지 정할 수 있다.
 */
public record StudentLessonListItemResponse(
    Long lessonId,
    LocalDate lessonDate,
    String title,
    String classRoomName,
    boolean hasVideo,
    boolean isNew,
    boolean viewed,
    String homeworkTitle
) {
}
