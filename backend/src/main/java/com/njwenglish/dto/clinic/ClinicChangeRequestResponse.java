package com.njwenglish.dto.clinic;

import com.njwenglish.entity.ClinicChangeRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import java.time.OffsetDateTime;

/** T-13 변경 요청 목록. to가 null이면 취소 요청이다. */
public record ClinicChangeRequestResponse(Long requestId,
                                          Long studentId,
                                          String studentName,
                                          ClinicSlotResponse from,
                                          ClinicSlotResponse to,
                                          String reasonCode,
                                          String reasonNote,
                                          ChangeRequestStatus status,
                                          OffsetDateTime requestedAt,
                                          OffsetDateTime decidedAt) {

    public static ClinicChangeRequestResponse from(ClinicChangeRequest request) {
        return new ClinicChangeRequestResponse(
            request.getId(),
            request.getStudent().getId(),
            request.getStudent().getName(),
            ClinicSlotResponse.from(request.getReservation().getClinic()),
            request.getTargetClinic() == null
                ? null : ClinicSlotResponse.from(request.getTargetClinic()),
            request.getReasonCode(),
            request.getReasonNote(),
            request.getStatus(),
            request.getCreatedAt(),
            request.getDecidedAt());
    }
}
