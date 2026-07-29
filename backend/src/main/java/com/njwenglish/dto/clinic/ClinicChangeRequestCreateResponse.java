package com.njwenglish.dto.clinic;

import com.njwenglish.entity.ClinicChangeRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;

public record ClinicChangeRequestCreateResponse(Long requestId, ChangeRequestStatus status) {

    public static ClinicChangeRequestCreateResponse from(ClinicChangeRequest request) {
        return new ClinicChangeRequestCreateResponse(request.getId(), request.getStatus());
    }
}
