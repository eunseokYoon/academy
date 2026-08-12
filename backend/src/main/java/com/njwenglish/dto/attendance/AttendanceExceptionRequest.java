package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** 안 온 학생 한 명. PRESENT를 예외로 보내는 건 무의미하지만 막지는 않는다. */
public record AttendanceExceptionRequest(@NotNull Long studentId,
                                         @NotNull AttendanceStatus status,
                                         String memo) {
}
