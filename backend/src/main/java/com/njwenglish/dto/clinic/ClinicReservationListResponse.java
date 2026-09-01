package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * T-13 명단. students는 <b>도착 시각 오름차순</b>이고, 같은 시각 안에서는 이름순이다.
 * 화면이 시각별로 묶어 그린다.
 *
 * <p>slots는 배정 화면에서 시각을 고르는 데 쓴다. 학생 화면과 같은 계산(Clinic.slots)이다.
 *
 * <p>attendanceConfirmed의 뜻이 슬롯 단위 확정 도입 이후 「학생이 있는 슬롯 전부 확정」으로
 * 바뀐다(2026-09-01). slotStates는 <b>Clinic.slots()와 실제 예약 시각의 합집합</b>이고
 * 시각 오름차순이다 — 슬롯 밖(outOfRange) 예약도 빠뜨리면 그 학생을 확정할 방법이 없어진다.
 */
public record ClinicReservationListResponse(Long clinicId,
                                            LocalDate clinicDate,
                                            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                            @JsonFormat(pattern = "HH:mm") List<LocalTime> slots,
                                            Short capacity,
                                            boolean attendanceConfirmed,
                                            List<ClinicReservationStudentResponse> students,
                                            List<ClinicSlotState> slotStates) {
}
