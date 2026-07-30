package com.njwenglish.dto.homework;

import com.njwenglish.entity.enums.SubmissionStatus;
import java.time.OffsetDateTime;

/**
 * S-2 목록. remainingMinutes는 <b>서버가</b> 계산한다. 음수면 마감이 지난 것이다 —
 * 클라이언트 시계는 틀릴 수 있어서 프론트에서 계산하면 사람마다 남은 시간이 달라진다.
 */
public record StudentHomeworkListItemResponse(Long homeworkId,
                                              String title,
                                              String classRoomName,
                                              OffsetDateTime dueAt,
                                              SubmissionStatus status,
                                              boolean isLate,
                                              int photoCount,
                                              boolean hasVideo,
                                              boolean hasFeedback,
                                              long remainingMinutes) {
}
