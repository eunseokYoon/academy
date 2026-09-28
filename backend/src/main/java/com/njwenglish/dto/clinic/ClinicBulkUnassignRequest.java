package com.njwenglish.dto.clinic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 요일 일괄 해제(2026-09-29). 기간 안의 그 요일 클리닉에서 학생들의 배정을 한꺼번에 뺀다.
 * 퇴원하거나 요일을 바꿀 때 쓴다. 한 회차 해제와 같이 행을 지우지 않고 CANCELED로 바꾼다.
 */
public record ClinicBulkUnassignRequest(
    @NotNull @Min(1) @Max(7) Short dayOfWeek,
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    @NotEmpty List<Long> studentIds
) {
}
