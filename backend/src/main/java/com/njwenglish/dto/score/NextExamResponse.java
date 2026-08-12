package com.njwenglish.dto.score;

import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.enums.ExamType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 학생·학부모 홈의 D-day. <b>일정이 없으면 null을 반환하고 프론트가 영역을 숨긴다.</b>
 * 0이나 임의 값을 채워 넣지 마라 — 시험이 오늘인 것으로 읽힌다.
 */
public record NextExamResponse(
    ExamType examType,
    LocalDate startDate,
    String scopeNote,
    int dDay
) {
    public static NextExamResponse from(ExamSchedule schedule, LocalDate today) {
        return new NextExamResponse(
            schedule.getExamType(),
            schedule.getStartDate(),
            schedule.getScopeNote(),
            (int) ChronoUnit.DAYS.between(today, schedule.getStartDate()));
    }
}
