package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/**
 * 도착 시각 슬롯 하나의 상태. <b>저장하지 않고 예약에서 파생한다</b>(2026-09-01 확정) —
 * arrival_time·attend_status가 이미 예약 행에 있어서 확정 상태를 담을 테이블이 필요 없다.
 *
 * <p>confirmed는 <b>예약이 1명 이상이고 전원 attend_status가 채워짐</b>이다.
 * 예약 0명인 슬롯은 확정할 학생이 없어 언제나 false이고, 화면은 「배정 없음」으로 그린다.
 */
public record ClinicSlotState(@JsonFormat(pattern = "HH:mm") LocalTime arrivalTime,
                              long reservedCount,
                              boolean confirmed) {
}
