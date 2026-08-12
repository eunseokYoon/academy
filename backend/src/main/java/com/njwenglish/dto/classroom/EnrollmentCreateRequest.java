package com.njwenglish.dto.classroom;

import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;

/** 이미 배정된 학생은 무시하고 나머지만 추가한다(멱등). joinedAt 생략 시 오늘. */
public record EnrollmentCreateRequest(@NotEmpty List<Long> studentIds, LocalDate joinedAt) {
}
