package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/** capacity를 생략하면 인원 제한 없음이다. 1회 정원이 미확정이라 기본값을 지어내지 않는다. */
public record ClinicCreateRequest(@NotNull LocalDate clinicDate,
                                  @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                  @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                  Short capacity,
                                  String memo) {
}
