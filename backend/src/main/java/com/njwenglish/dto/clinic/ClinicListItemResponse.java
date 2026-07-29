package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.enums.ClinicStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/** T-13 목록. attendanceConfirmed는 clinics에 컬럼이 없어 예약의 attend_status로 판정한다. */
public record ClinicListItemResponse(Long clinicId,
                                     LocalDate clinicDate,
                                     @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                     @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                     Short capacity,
                                     long reservedCount,
                                     ClinicStatus status,
                                     boolean attendanceConfirmed,
                                     String memo) {

    public static ClinicListItemResponse of(Clinic clinic, long reservedCount,
                                            boolean attendanceConfirmed) {
        return new ClinicListItemResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime(), clinic.getCapacity(),
            reservedCount, clinic.getStatus(), attendanceConfirmed, clinic.getMemo());
    }
}
