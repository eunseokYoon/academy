package com.njwenglish.dto.clinic;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 선생님 배정(복수). 이미 신청한 학생은 무시하고 나머지만 넣는다(멱등).
 * 정원을 넘으면 넘는 만큼만 넣지 말고 전체를 409로 거절한다 —
 * 일부만 들어가면 선생님이 누가 빠졌는지 모른다.
 */
public record ClinicAssignRequest(@NotEmpty List<Long> studentIds) {
}
