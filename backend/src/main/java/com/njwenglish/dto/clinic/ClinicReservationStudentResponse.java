package com.njwenglish.dto.clinic;

import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.AttendanceStatus;

/**
 * T-13 명단 한 줄. <b>선생님 화면 전용이다.</b>
 * 학생 화면(S-9)에는 인원 수만 내려가고 다른 학생 이름은 나가지 않는다.
 */
public record ClinicReservationStudentResponse(Long reservationId,
                                               Long studentId,
                                               String name,
                                               boolean assignedByTeacher,
                                               AttendanceStatus attendStatus,
                                               String memo) {

    public static ClinicReservationStudentResponse from(ClinicReservation reservation) {
        return new ClinicReservationStudentResponse(
            reservation.getId(),
            reservation.getStudent().getId(),
            reservation.getStudent().getName(),
            reservation.isAssignedByTeacher(),
            reservation.getAttendStatus(),
            reservation.getMemo());
    }
}
