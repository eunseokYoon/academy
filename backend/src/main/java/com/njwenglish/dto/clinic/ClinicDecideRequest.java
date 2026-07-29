package com.njwenglish.dto.clinic;

import jakarta.validation.constraints.NotNull;

/**
 * 승인·거절.
 *
 * <p>note는 <b>저장되지 않는다.</b> clinic_change_requests에 결정 메모 컬럼이 없고,
 * 학생·학부모 어느 화면에도 노출 자리가 없다. 확정된 스키마를 임의로 늘리지 않으려고
 * 받기만 하고 버린다. 거절 사유를 남겨야 한다면 컬럼부터 확정해야 한다.
 */
public record ClinicDecideRequest(@NotNull Boolean approve, String note) {
}
