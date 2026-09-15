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
 * <p><b>분모는 Clinic.slots().size()가 아니다</b>(2026-09-01). 아무도 배정되지 않은 시각은
 * 확정할 것이 없어서, 분모에 넣으면 「2/5」가 영영 「5/5」가 되지 않는다.
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
                                     /** 학생이 1명 이상 배정된 슬롯 수. 배지의 분모다. */
                                     int studentSlotCount,
                                     /** 그중 전원 확정된 슬롯 수. 배지의 분자다. */
                                     int confirmedSlotCount,
                                     String weekLabel,
                                     String memo) {

    public static ClinicListItemResponse of(Clinic clinic, long reservedCount,
                                            boolean attendanceConfirmed,
                                            int studentSlotCount, int confirmedSlotCount) {
        return new ClinicListItemResponse(clinic.getId(), clinic.getClinicDate(),
            clinic.getStartTime(), clinic.getEndTime(), clinic.getCapacity(),
            reservedCount, clinic.getStatus(), attendanceConfirmed,
            studentSlotCount, confirmedSlotCount,
            MonthWeeks.label(clinic.getClinicDate()), clinic.getMemo());
    }
}
