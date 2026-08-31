package com.njwenglish.dto.clinic;

import java.time.LocalDate;
import java.util.List;

/**
 * <b>일부만 실패해도 전체를 되돌리지 않는다.</b> 18회 중 1회가 정원이 찼다고 17회를
 * 못 넣으면 쓸 수 없는 기능이 된다. 단건 신청(409·400)과 여기가 다른 점이다.
 */
public record ClinicSeriesReserveResponse(
    int reserved, int skipped,
    List<LocalDate> reservedDates,
    List<Skipped> skippedItems
) {
    /** reason: ALREADY(이미 신청) · CAPACITY(정원 초과) · NO_SLOT(그 회차에 해당 슬롯 없음) */
    public record Skipped(LocalDate clinicDate, String reason) {
    }
}
