package com.njwenglish.dto.attendance;

import java.time.LocalDate;

/**
 * T-5 주차 조회의 수업 한 줄. {@link PendingLessonResponse}와 달리 <b>확정된 것도 담는다</b> —
 * confirmed로 구분한다(2026-09-01 확정).
 *
 * <p>미래 수업은 여기에도 들어오지 않는다. 목적이 "지난 걸 보고 고치기"라서다.
 */
public record WeekLessonResponse(Long lessonId,
                                 Long classRoomId,
                                 String classRoomName,
                                 LocalDate lessonDate,
                                 long studentCount,
                                 boolean confirmed) {
}
