package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.AttendanceStatus;
import java.time.LocalTime;

/**
 * T-13 명단 한 줄. <b>선생님 화면 전용이다.</b>
 * 학생 화면(S-9)에는 인원 수만 내려가고 다른 학생 이름은 나가지 않는다.
 *
 * <p>outOfRange는 선생님이 클리닉 시간대를 좁혔을 때 범위 밖으로 남은 예약이다.
 * 서버가 임의로 옮기지 않는다 — 이미 약속된 시각을 말없이 바꾸면 학생이 헛걸음한다.
 * 선생님이 보고 직접 처리하라고 표시만 한다.
 */
public record ClinicReservationStudentResponse(Long reservationId,
                                               Long studentId,
                                               String name,
                                               @JsonFormat(pattern = "HH:mm")
                                               LocalTime arrivalTime,
                                               boolean outOfRange,
                                               boolean assignedByTeacher,
                                               AttendanceStatus attendStatus,
                                               String memo) {

    public static ClinicReservationStudentResponse from(ClinicReservation reservation) {
        return new ClinicReservationStudentResponse(
            reservation.getId(),
            reservation.getStudent().getId(),
            reservation.getStudent().getName(),
            reservation.getArrivalTime(),
            !reservation.getClinic().hasSlot(reservation.getArrivalTime()),
            reservation.isAssignedByTeacher(),
            reservation.getAttendStatus(),
            reservation.getMemo());
    }
}
