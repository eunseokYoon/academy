package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * P-2. 학부모는 자녀의 일정과 출석만 본다 — 신청·취소·변경 API를 열지 마라.
 * attendStatus가 null이면 아직 출석 확정 전이다.
 */
public record ParentClinicResponse(Long clinicId,
                                   LocalDate clinicDate,
                                   @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                   AttendanceStatus attendStatus,
                                   ChangeRequestStatus changeRequestStatus) {
}
