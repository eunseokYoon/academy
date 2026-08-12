package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.ClinicReservation;
import com.njwenglish.entity.enums.ReservationStatus;
import java.time.LocalTime;

public record ClinicReservationCreateResponse(Long reservationId,
                                              Long clinicId,
                                              @JsonFormat(pattern = "HH:mm")
                                              LocalTime arrivalTime,
                                              ReservationStatus status) {

    public static ClinicReservationCreateResponse from(ClinicReservation reservation) {
        return new ClinicReservationCreateResponse(reservation.getId(),
            reservation.getClinic().getId(), reservation.getArrivalTime(),
            reservation.getStatus());
    }
}
