package com.njwenglish.dto.home;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.ClinicReservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * P-1 홈의 다음 클리닉. 없으면 null이고 프론트가 카드를 숨긴다.
 *
 * <p><b>시각은 클리닉 시간대의 시작이 아니라 이 학생이 고른 도착 시각이다.</b>
 * 클리닉은 17:00~22:00처럼 다섯 시간짜리 시간대이고 학생은 그 안에서 한 시간을 고른다
 * (2026-08-10). 시간대 시작을 내려주면 22시에 가기로 한 학생의 학부모가 17시에 보낸다.
 *
 * <p>다른 학생 이름·인원은 넣지 않는다. 학부모는 자녀 일정만 본다.
 */
public record NextClinicResponse(
    Long clinicId,
    LocalDate clinicDate,
    @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
    int dDay
) {
    /**
     * 예약을 받는다. 클리닉만 받으면 도착 시각을 알 수 없어 시간대 시작을 쓰게 된다 —
     * 실제로 그렇게 잘못 짜여 있었다.
     */
    public static NextClinicResponse from(ClinicReservation reservation, LocalDate today) {
        LocalDate date = reservation.getClinic().getClinicDate();
        return new NextClinicResponse(
            reservation.getClinic().getId(), date, reservation.getArrivalTime(),
            (int) ChronoUnit.DAYS.between(today, date));
    }
}
