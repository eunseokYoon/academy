package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * 시리즈 일괄 신청. 시리즈에 id가 없으므로 조회에서 받은 세 값이 곧 키다.
 *
 * <p>arrivalTime은 클라이언트가 보내는 값이라 <b>서버가 회차마다 검사한다</b>
 * ({@code Clinic.hasSlot}). 화면이 슬롯 목록을 그려 주는 건 안내일 뿐이다.
 */
public record ClinicSeriesReserveRequest(
    @NotNull @Min(1) @Max(7) Short dayOfWeek,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime
) {
}
