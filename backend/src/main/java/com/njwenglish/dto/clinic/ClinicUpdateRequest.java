package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.enums.ClinicStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 부분 수정. null인 필드는 그대로 둔다.
 *
 * <p>capacity만 예외다 — "제한 없음으로 바꾸기"와 "안 건드리기"가 둘 다 null이라
 * 구분이 안 된다. 그래서 clearCapacity를 따로 받는다.
 */
public record ClinicUpdateRequest(LocalDate clinicDate,
                                  @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                  @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                  Short capacity,
                                  boolean clearCapacity,
                                  String memo,
                                  ClinicStatus status) {
}
