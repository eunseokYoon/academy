package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * S-9 시간 변경·클리닉 이동. <b>선생님 승인이 없다</b>(2026-08-10 확정) — 즉시 반영된다.
 *
 * <p>targetClinicId를 생략하면 같은 클리닉 안에서 도착 시각만 옮기고,
 * 넣으면 그 클리닉으로 이동한다.
 *
 * <p>reason은 필수다. 승인 절차가 없어서 <b>이 문장이 선생님에게 남는 유일한 설명</b>이다.
 * 비워 둘 수 있게 만들지 마라.
 */
public record ClinicReservationChangeRequest(
    Long targetClinicId,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
    @NotBlank @Size(max = 500) String reason) {
}
