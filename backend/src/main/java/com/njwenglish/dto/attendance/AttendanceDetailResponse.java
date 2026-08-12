package com.njwenglish.dto.attendance;

import com.njwenglish.entity.Attendance;
import com.njwenglish.entity.enums.AttendanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 개별 정정(PATCH) 결과. updatedAt이 채워졌는지로 정정 여부를 확인한다. */
public record AttendanceDetailResponse(Long attendanceId,
                                       Long lessonId,
                                       Long studentId,
                                       String name,
                                       LocalDate attendDate,
                                       AttendanceStatus status,
                                       String memo,
                                       OffsetDateTime checkedAt,
                                       OffsetDateTime updatedAt) {

    public static AttendanceDetailResponse from(Attendance attendance) {
        return new AttendanceDetailResponse(
            attendance.getId(),
            attendance.getLesson() == null ? null : attendance.getLesson().getId(),
            attendance.getStudent().getId(),
            attendance.getStudent().getName(),
            attendance.getAttendDate(),
            attendance.getStatus(),
            attendance.getMemo(),
            attendance.getCheckedAt(),
            attendance.getUpdatedAt());
    }
}
