package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.entity.Clinic;
import com.njwenglish.entity.enums.ClinicStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * T-13 목록. attendanceConfirmed는 clinics에 컬럼이 없어 예약의 attend_status로 판정한다.
 *
 * <p>weekLabel("8월 2주")은 화면이 주차별로 묶는 데 쓴다. <b>프론트에서 날짜로 다시 만들지 마라</b> —
 * 계산도 문자열도 {@link MonthWeeks}가 정본이고, 학생 화면(S-9)도 같은 값을 받는다.
 */
public record ClinicListItemResponse(Long clinicId,
                                     LocalDate clinicDate,
                                     @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                     @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                     Short capacity,
                                     long reservedCount,
                                     ClinicStatus status,
                                     boolean attendanceConfirmed,
                                     String weekLabel,
                                     String memo) {

    public static ClinicListItemResponse of(Clinic clinic, long reservedCount,
                                            boolean attendanceConfirmed) {
        return new ClinicListItemResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime(), clinic.getCapacity(),
            reservedCount, clinic.getStatus(), attendanceConfirmed,
            MonthWeeks.label(clinic.getClinicDate()), clinic.getMemo());
    }
}
