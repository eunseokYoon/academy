package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.OffsetDateTime;

public record AttendanceConfirmResponse(Long lessonId,
                                        LessonAttendanceStatus attendanceStatus,
                                        OffsetDateTime confirmedAt,
                                        AttendanceSummaryResponse summary) {
}
