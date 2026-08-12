package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * S-9 신청. arrivalTime은 클리닉의 슬롯 목록에 있는 값이어야 한다 —
 * 서버가 {@code Clinic.hasSlot}으로 검사한다. 화면이 목록을 그려 주는 건 안내일 뿐이다.
 */
public record ClinicReservationCreateRequest(
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime) {
}
