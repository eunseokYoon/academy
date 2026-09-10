package com.njwenglish.dto.attendance;

import java.time.LocalDate;

/**
 * S-6 · P-2 캘린더의 하루.
 *
 * <p><b>homeworkRate는 항상 null이다</b>(2026-09-10). 날짜 칸의 띠를 없앴다 —
 * 50% 미만이면 빨강이라 학부모 캘린더가 빨개졌다. 월 합계는
 * AttendanceCalendarResponse.homeworkCompletionRate에 그대로 있다.
 *
 * <p><b>필드를 지우지 마라.</b> 배포 순서가 backend → web이라 필드가 사라지면
 * 옛 화면에서 undefined !== null이 참이 되어 NaN% 그라디언트가 그려진다.
 */
public record AttendanceDayResponse(LocalDate date, String status, Integer homeworkRate) {

    public static final String PENDING = "PENDING";
}
