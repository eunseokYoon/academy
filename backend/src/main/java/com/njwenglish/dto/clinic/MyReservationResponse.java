package com.njwenglish.dto.clinic;

import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ReservationStatus;

/**
 * S-9의 "내 클리닉". changeRequestStatus는 대기 중인 변경 요청이 있을 때만 값이 있다.
 *
 * <p>attendStatus가 null이면 <b>결석이 아니라 아직 출석 확정 전</b>이다. 수업 캘린더의
 * PENDING과 같은 뜻이고, 화면에서도 같은 회색 "미확인"으로 그려야 한다.
 * 학부모 화면(P-4)의 ParentClinicResponse와 같은 규칙이다.
 */
public record MyReservationResponse(Long reservationId,
                                    ReservationStatus status,
                                    AttendanceStatus attendStatus,
                                    ChangeRequestStatus changeRequestStatus) {
}
