package com.njwenglish.dto.attendance;

import java.time.LocalDate;

/**
 * 캘린더 한 칸. status는 출석 상태이거나 "PENDING"(미확인)이다.
 *
 * <p>homeworkRate는 Phase 5 완료 전까지 null이다. 그날 숙제가 없는 경우도 null이며
 * 0으로 바꾸지 마라 — 0은 빨간 띠라서 학부모에게 "하나도 안 냈다"로 보인다.
 */
public record AttendanceDayResponse(LocalDate date, String status, Integer homeworkRate) {

    public static final String PENDING = "PENDING";
}
