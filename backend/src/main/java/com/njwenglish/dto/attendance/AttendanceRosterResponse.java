package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.LessonAttendanceStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-5 출석 입력 화면 데이터.
 *
 * <p>loadedAt은 <b>이 응답을 만든 서버 시각</b>이다. 확정 요청이 그대로 돌려보낸다 —
 * 성적 그리드(7-0)와 같은 장치다.
 */
public record AttendanceRosterResponse(Long lessonId,
                                       Long classRoomId,
                                       String classRoomName,
                                       LocalDate lessonDate,
                                       LessonAttendanceStatus attendanceStatus,
                                       List<AttendanceStudentResponse> students,
                                       OffsetDateTime loadedAt) {
}
