package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * S-9 시리즈 카드. 「매주 화요일 17:00~22:00 · 9/2~12/30 · 총 18회」 한 줄이다.
 *
 * <p><b>시리즈는 테이블이 아니라 조회다.</b> 오늘 이후의 OPEN 클리닉을
 * (요일, 시작, 종료)로 묶은 결과이고 id가 없다 — 그래서 신청 요청이 이 세 값을
 * 그대로 되돌려 보낸다. 시리즈 테이블을 두면 선생님이 특정 회차를 지우거나 시간을
 * 고칠 때 실제 클리닉과 어긋나고, 그 불일치를 맞추는 코드가 계속 따라붙는다.
 *
 * <p>slots는 그 시리즈 클리닉들의 공통 도착 시각이다. 화면이 시각으로 다시 계산하지 마라 —
 * 정본은 {@code Clinic.slots()}이고, 어긋나면 학생이 고른 시각을 서버가 거절한다.
 */
public record ClinicSeriesResponse(
    short dayOfWeek,
    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    LocalDate firstDate,
    LocalDate lastDate,
    int totalCount,
    /** 이 시리즈에서 내가 이미 신청한 회차 수. 전부 신청했으면 버튼을 막는다. */
    int reservedCount,
    @JsonFormat(pattern = "HH:mm") List<LocalTime> slots
) {
}
