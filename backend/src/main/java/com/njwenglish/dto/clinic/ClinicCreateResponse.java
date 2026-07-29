package com.njwenglish.dto.clinic;

import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.enums.ClinicStatus;

public record ClinicCreateResponse(Long clinicId,
                                   long reservedCount,
                                   Short capacity,
                                   ClinicStatus status) {

    public static ClinicCreateResponse of(Clinic clinic, long reservedCount) {
        return new ClinicCreateResponse(clinic.getId(), reservedCount,
            clinic.getCapacity(), clinic.getStatus());
    }
}
