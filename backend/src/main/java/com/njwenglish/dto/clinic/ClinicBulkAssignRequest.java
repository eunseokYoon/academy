package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 요일 일괄 배정(2026-09-29). 기간 안의 그 요일에 <b>이미 열려 있는</b> 클리닉 전부에
 * 학생들을 넣는다. 시리즈를 저장하지 않으므로 나중에 연 회차에는 자동으로 들어가지 않는다 —
 * 선생님이 한 번 더 누른다(이미 배정된 학생은 건너뛰어 여러 번 눌러도 안전하다).
 *
 * <p>요일 표기는 {@link ClinicBulkCreateRequest}와 같은 1=월 … 7=일이다.
 * arrivalTime을 생략하면 그날 클리닉의 시작 시각이다({@link ClinicAssignRequest}와 같다).
 */
public record ClinicBulkAssignRequest(
    @NotNull @Min(1) @Max(7) Short dayOfWeek,
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
    @NotEmpty List<Long> studentIds
) {
}
