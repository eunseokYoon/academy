package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 기간 안의 특정 요일에 클리닉을 한꺼번에 연다. 9~12월 매주 화요일을 손으로 18번
 * 만들게 하면 실제로 안 쓴다. {@code LessonBulkCreateRequest}와 같은 모양이다.
 *
 * <p><b>요일을 직접 받는다.</b> 수업은 반 스케줄에서 요일을 가져오지만 클리닉에는
 * 그럴 근거가 없다. 표기는 class_room_schedules와 같은 1=월 … 7=일이다.
 *
 * <p>skipDates와 <b>이미 OPEN인 날짜</b>는 건너뛴다. 전부 실패시키지 않는다 —
 * 그러면 선생님이 어느 날짜가 걸렸는지 찾아 지우고 다시 눌러야 한다.
 *
 * <p>기간에 상한이 없다. 수업 일괄 생성도 없어서 맞췄다. from/to를 크게 잡으면
 * 그만큼 행이 생기고 되돌리려면 하나씩 지워야 한다.
 */
public record ClinicBulkCreateRequest(
    @NotNull @Min(1) @Max(7) Short dayOfWeek,
    @NotNull LocalDate from,
    @NotNull LocalDate to,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
    Short capacity,
    String memo,
    List<LocalDate> skipDates
) {
}
