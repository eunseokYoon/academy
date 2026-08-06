package com.njwenglish.dto.home;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.njwenglish.entity.ClassRoomSchedule;
import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * S-1 홈의 다음 수업. <b>없으면 null이고 프론트가 카드를 숨긴다.</b>
 * 0이나 임의 값을 채우지 마라 — 오늘이 수업인 것으로 읽힌다.
 *
 * <p>수업 제목·내용은 넣지 않는다. 홈에서는 "언제, 어느 반"까지가 전부다.
 *
 * <p>startTime은 <b>lessons가 아니라 반의 요일 슬롯</b>에서 온다. 그 요일 슬롯이 없으면
 * null이고 프론트는 시각 없이 날짜만 그린다 — 시각을 지어내 채우지 마라.
 */
public record NextLessonResponse(LocalDate lessonDate,
                                 @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                 int dDay,
                                 String classRoomName) {

    public static NextLessonResponse from(Lesson lesson, LocalDate today) {
        return new NextLessonResponse(
            lesson.getLessonDate(),
            lesson.getClassRoom().scheduleOn(lesson.getLessonDate())
                .map(ClassRoomSchedule::getStartTime)
                .orElse(null),
            (int) ChronoUnit.DAYS.between(today, lesson.getLessonDate()),
            lesson.getClassRoom().getName());
    }
}
