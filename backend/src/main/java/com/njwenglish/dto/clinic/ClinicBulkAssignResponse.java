package com.njwenglish.dto.clinic;

import java.time.LocalDate;
import java.util.List;

/**
 * @param clinics      배정을 처리한 회차 수(이미 전원 배정돼 있던 회차도 센다)
 * @param reservations 새로 생긴 예약 행 수
 * @param skipped      건너뛴 날짜와 이유. 전부 실패시키지 않는다 — 18회 중 1회가 정원 초과라고
 *                     17회를 못 넣으면 쓸 수 없는 기능이 된다
 */
public record ClinicBulkAssignResponse(int clinics, int reservations, List<Skipped> skipped) {

    public record Skipped(LocalDate date, Reason reason) {
    }

    public enum Reason {
        /** 오늘보다 앞. 지난 회차에 넣으면 출결 미확정으로 T-1에 쌓인다 */
        PAST,
        /** 그날 열린 클리닉이 없다 */
        NO_CLINIC,
        /** 열린 클리닉은 있지만 그 도착 시각이 시간대 밖이다 */
        NO_SLOT,
        /** 정원 초과. 그 회차에는 아무도 넣지 않는다(일부만 들어가면 누가 빠졌는지 모른다) */
        FULL
    }
}
