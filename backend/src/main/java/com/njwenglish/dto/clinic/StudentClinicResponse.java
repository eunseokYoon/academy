package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * S-9 신청 화면. <b>다른 학생 이름을 담지 않는다.</b> 인원 수만이다.
 *
 * <p>full은 서버가 계산한다. capacity가 null이면 제한 없음이라 언제나 false다.
 * 프론트에서 reservedCount >= capacity를 계산하게 두면 null일 때 깨진다.
 */
public record StudentClinicResponse(Long clinicId,
                                    LocalDate clinicDate,
                                    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                    Short capacity,
                                    long reservedCount,
                                    boolean full,
                                    MyReservationResponse myReservation) {
}
