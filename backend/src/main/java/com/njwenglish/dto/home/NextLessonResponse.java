package com.njwenglish.dto.home;

import com.njwenglish.entity.Lesson;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * S-1 홈의 다음 수업. <b>없으면 null이고 프론트가 카드를 숨긴다.</b>
 * 0이나 임의 값을 채우지 마라 — 오늘이 수업인 것으로 읽힌다.
 *
 * <p>수업 제목·내용은 넣지 않는다. 홈에서는 "언제, 어느 반"까지가 전부다.
 */
public record NextLessonResponse(LocalDate lessonDate, int dDay, String classRoomName) {

    public static NextLessonResponse from(Lesson lesson, LocalDate today) {
        return new NextLessonResponse(
            lesson.getLessonDate(),
            (int) ChronoUnit.DAYS.between(today, lesson.getLessonDate()),
            lesson.getClassRoom().getName());
    }
}
