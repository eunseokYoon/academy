package com.njwenglish.dto.clinic;

/**
 * @param canceled 해제한 예약 수
 * @param kept     출결이 이미 기록돼 있어 남겨 둔 예약 수. 지우면 그날 출석 기록이 사라진다
 */
public record ClinicBulkUnassignResponse(int canceled, int kept) {
}
