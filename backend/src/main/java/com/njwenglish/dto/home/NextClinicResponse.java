package com.njwenglish.dto.home;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.Clinic;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * P-1 홈의 다음 클리닉. 없으면 null이고 프론트가 카드를 숨긴다.
 *
 * <p>다른 학생 이름·인원은 넣지 않는다. 학부모는 자녀 일정만 본다.
 */
public record NextClinicResponse(
    Long clinicId,
    LocalDate clinicDate,
    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    int dDay
) {
    public static NextClinicResponse from(Clinic clinic, LocalDate today) {
        return new NextClinicResponse(
            clinic.getId(), clinic.getClinicDate(), clinic.getStartTime(),
            (int) ChronoUnit.DAYS.between(today, clinic.getClinicDate()));
    }
}
