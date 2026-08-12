package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalTime;
import java.util.List;

/**
 * 선생님 배정(복수). 이미 신청한 학생은 무시하고 나머지만 넣는다(멱등).
 * 정원을 넘으면 넘는 만큼만 넣지 말고 전체를 409로 거절한다 —
 * 일부만 들어가면 선생님이 누가 빠졌는지 모른다.
 *
 * <p>arrivalTime을 생략하면 클리닉 시작 시각이다. 고른 시각으로 명단 전원이 배정된다 —
 * 한 번에 여러 명을 넣는 화면이라 학생마다 다른 시각을 받지 않는다.
 */
public record ClinicAssignRequest(@NotEmpty List<Long> studentIds,
                                  @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime) {
}
