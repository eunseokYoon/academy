package com.njwenglish.dto.clinic;

import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ReservationStatus;

/** S-9의 "내 클리닉". changeRequestStatus는 대기 중인 변경 요청이 있을 때만 값이 있다. */
public record MyReservationResponse(Long reservationId,
                                    ReservationStatus status,
                                    ChangeRequestStatus changeRequestStatus) {
}
