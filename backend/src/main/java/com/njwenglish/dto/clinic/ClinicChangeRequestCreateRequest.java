package com.njwenglish.dto.clinic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 학생의 시간 변경 요청. targetClinicId가 null이면 취소 요청이다.
 *
 * <p>reasonCode는 필수지만 <b>옵션 목록이 미확정이라 서버는 문자열로 받아 저장만 한다.</b>
 * enum도 CHECK 제약도 만들지 말고, 값을 그럴듯하게 지어내지도 마라.
 */
public record ClinicChangeRequestCreateRequest(@NotNull Long reservationId,
                                               Long targetClinicId,
                                               @NotBlank String reasonCode,
                                               String reasonNote) {
}
