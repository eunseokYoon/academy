package com.njwenglish.dto.classroom;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.ClassRoomSchedule;
import java.time.LocalTime;

/**
 * 요청·응답 공용. endTime은 선택이다 — 기존 반 이관분에는 종료시각이 없다.
 *
 * <p>dayOfWeek: 1=월 ~ 7=일 (ISO-8601)
 *
 * <p>시각은 "19:00"이다. 기본 직렬화는 "19:00:00"으로 나가는데, HTML input[type=time]이
 * 초가 붙은 값을 안 받는다. 대시보드(TeacherDashboardResponse.Lesson)도 같은 형식을 쓴다.
 */
public record ClassRoomScheduleDto(
    Short dayOfWeek,
    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
    @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {
    public static ClassRoomScheduleDto from(ClassRoomSchedule schedule) {
        return new ClassRoomScheduleDto(
            schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime());
    }
}
