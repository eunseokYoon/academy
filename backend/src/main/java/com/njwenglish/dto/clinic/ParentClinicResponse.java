package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.enums.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * P-2. 학부모는 자녀의 일정과 출석만 본다 — 신청·취소·변경 API를 열지 마라.
 * attendStatus가 null이면 아직 출석 확정 전이다.
 *
 * <p><b>arrivalTime이 핵심이다.</b> 클리닉 시간대만 내리면 17:00~22:00 다섯 시간짜리로
 * 보여서 자녀가 몇 시에 가는지 학부모가 알 수 없다.
 */
public record ParentClinicResponse(Long clinicId,
                                   LocalDate clinicDate,
                                   @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
                                   AttendanceStatus attendStatus) {
}
