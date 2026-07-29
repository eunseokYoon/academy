package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.util.List;

/** T-5 출석 입력 화면 데이터. */
public record AttendanceRosterResponse(Long lessonId,
                                       Long classRoomId,
                                       String classRoomName,
                                       LocalDate lessonDate,
                                       LessonAttendanceStatus attendanceStatus,
                                       List<AttendanceStudentResponse> students) {
}
