package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.common.util.MonthWeeks;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * S-9 신청 화면. <b>다른 학생 이름을 담지 않는다.</b> 인원 수만이다.
 *
 * <p>full은 서버가 계산한다. capacity가 null이면 제한 없음이라 언제나 false다.
 * 프론트에서 reservedCount >= capacity를 계산하게 두면 null일 때 깨진다.
 *
 * <p>slots는 고를 수 있는 도착 시각이다. <b>서버가 계산해 내려준다</b> —
 * 프론트에서 시작·종료로 다시 만들면 "마지막 슬롯은 종료 1시간 전" 규칙이 두 곳으로 갈라진다.
 * 정원은 클리닉 전체 기준이라 슬롯별 잔여 인원은 없다(확정).
 *
 * <p>weekLabel("8월 2주")은 화면이 주차별로 묶는 데 쓴다. <b>프론트에서 날짜로 다시 만들지 마라</b> —
 * "달 안에서 1일부터 7일씩" 규칙이 두 곳으로 갈라지면 수업·성적의 주차와 클리닉의 주차가
 * 서로 다른 날을 가리키게 된다. 계산도 문자열도 {@link MonthWeeks}가 정본이다.
 */
public record StudentClinicResponse(Long clinicId,
                                    LocalDate clinicDate,
                                    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                    @JsonFormat(pattern = "HH:mm") List<LocalTime> slots,
                                    Short capacity,
                                    long reservedCount,
                                    boolean full,
                                    String weekLabel,
                                    MyReservationResponse myReservation) {
}
