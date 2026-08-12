package com.njwenglish.dto.score;

import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.enums.ExamType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 학생·학부모용. 반 이름은 필요 없고 dDay가 붙는다.
 * dDay가 음수면 이미 지난 시험이다 — 프론트가 회색으로 내린다.
 */
public record StudentExamScheduleResponse(
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    String scopeNote,
    int dDay
) {
    public static StudentExamScheduleResponse from(ExamSchedule schedule, LocalDate today) {
        return new StudentExamScheduleResponse(
            schedule.getExamType(),
            schedule.getStartDate(),
            schedule.getEndDate(),
            schedule.getScopeNote(),
            (int) ChronoUnit.DAYS.between(today, schedule.getStartDate()));
    }
}
