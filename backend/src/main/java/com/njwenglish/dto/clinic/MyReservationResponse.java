package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ReservationStatus;
import java.time.LocalTime;

/**
 * S-9의 "내 클리닉". arrivalTime이 학생이 고른 도착 시각이다.
 *
 * <p>attendStatus가 null이면 <b>결석이 아니라 아직 출석 확정 전</b>이다. 수업 캘린더의
 * PENDING과 같은 뜻이고, 화면에서도 같은 회색 "미확인"으로 그려야 한다.
 * 학부모 화면(P-4)의 ParentClinicResponse와 같은 규칙이다.
 *
 * <p>changeRequestStatus는 없앴다 — 변경에 승인 단계가 없어서 "요청 중" 상태 자체가 없다.
 */
public record MyReservationResponse(Long reservationId,
                                    ReservationStatus status,
                                    @JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
                                    AttendanceStatus attendStatus) {
}
