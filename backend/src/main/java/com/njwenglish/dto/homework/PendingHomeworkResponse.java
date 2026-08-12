package com.njwenglish.dto.homework;

import java.time.OffsetDateTime;

/**
 * T-1 대시보드. 선생님이 손대야 할 것만 센다.
 *
 * <p>"확인 대기"는 없다 — 낸 것은 그 순간 ⭕가 되어 선생님이 할 일이 없다.
 * 남은 할 일은 안 낸 학생뿐이다.
 */
public record PendingHomeworkResponse(Long homeworkId,
                                      String title,
                                      String classRoomName,
                                      OffsetDateTime dueAt,
                                      long notSubmitted) {
}
