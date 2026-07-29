package com.njwenglish.dto.attendance;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 안 온 학생만 담는다. 빈 배열은 "전원 출석"이라는 유효한 요청이다 — 400으로 막지 마라.
 * null도 빈 배열과 같게 다룬다.
 */
public record AttendanceConfirmRequest(@Valid List<AttendanceExceptionRequest> exceptions) {

    public List<AttendanceExceptionRequest> exceptionsOrEmpty() {
        return exceptions == null ? List.of() : exceptions;
    }
}
