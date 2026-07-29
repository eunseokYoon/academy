package com.njwenglish.dto.attendance;

import java.time.LocalDate;

/** 미확정 수업 한 줄. 미래 수업은 아직 확정할 수 없으므로 목록에서 제외한다. */
public record PendingLessonResponse(Long lessonId,
                                    Long classRoomId,
                                    String classRoomName,
                                    LocalDate lessonDate,
                                    long studentCount) {
}
