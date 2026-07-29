package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.Clinic;
import java.time.LocalDate;
import java.time.LocalTime;

/** 변경 요청의 from/to에 쓰는 최소 정보. */
public record ClinicSlotResponse(Long clinicId,
                                 LocalDate clinicDate,
                                 @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                 @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    public static ClinicSlotResponse from(Clinic clinic) {
        return new ClinicSlotResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime());
    }
}
