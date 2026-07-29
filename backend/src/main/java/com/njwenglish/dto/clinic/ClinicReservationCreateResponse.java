package com.njwenglish.dto.clinic;

import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.ReservationStatus;

public record ClinicReservationCreateResponse(Long reservationId,
                                              Long clinicId,
                                              ReservationStatus status) {

    public static ClinicReservationCreateResponse from(ClinicReservation reservation) {
        return new ClinicReservationCreateResponse(reservation.getId(),
            reservation.getClinic().getId(), reservation.getStatus());
    }
}
