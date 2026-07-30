package com.njwenglish.dto.homework;

import java.time.OffsetDateTime;

/** T-1 대시보드. 선생님이 손대야 할 것만 센다. */
public record PendingHomeworkResponse(Long homeworkId,
                                      String title,
                                      String classRoomName,
                                      OffsetDateTime dueAt,
                                      long notSubmitted,
                                      long awaitingCheck) {
}
