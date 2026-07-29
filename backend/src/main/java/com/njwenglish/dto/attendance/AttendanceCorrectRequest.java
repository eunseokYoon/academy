package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** 확정 후 개별 정정. 학부모 문의는 반드시 들어오므로 누가 언제 바꿨는지 남긴다. */
public record AttendanceCorrectRequest(@NotNull AttendanceStatus status, String memo) {
}
