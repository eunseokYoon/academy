package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

/**
 * T-13 출결 확정. <b>도착 시각 슬롯 하나를 확정한다</b>(2026-09-01 확정) —
 * 5시간짜리 클리닉을 한 번에 확정하면 17시 학생과 21시 학생이 같은 시점에 확정된다.
 *
 * <p>수업 출석(T-5)의 {@code AttendanceConfirmRequest}를 재사용하지 않는 이유가 있다.
 * 그쪽은 반 단위라 도착 시각이라는 개념이 없다. 공유 DTO에 arrivalTime을 넣으면
 * 클리닉에만 있는 개념이 수업 출석으로 샌다.
 *
 * <p>exceptions는 <b>안 온 학생만</b> 담는다. 빈 배열은 "그 시각 전원 출석"이라는
 * 유효한 요청이다 — 400으로 막지 마라.
 */
public record ClinicAttendanceConfirmRequest(
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
    @Valid List<AttendanceExceptionRequest> exceptions) {

    public List<AttendanceExceptionRequest> exceptionsOrEmpty() {
        return exceptions == null ? List.of() : exceptions;
    }
}
