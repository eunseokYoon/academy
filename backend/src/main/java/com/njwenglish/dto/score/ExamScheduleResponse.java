package com.njwenglish.dto.score;

import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.enums.ExamType;
import java.time.LocalDate;

/** T-11 격자용. 미등록 칸이 눈에 보여야 해서 등록된 것만 내려주고 프론트가 격자를 채운다. */
public record ExamScheduleResponse(
    Long examScheduleId,
    Long classRoomId,
    String classRoomName,
    Short year,
    Short semester,
    ExamType examType,
    LocalDate startDate,
    LocalDate endDate,
    String scopeNote
) {
    public static ExamScheduleResponse from(ExamSchedule schedule) {
        return new ExamScheduleResponse(
            schedule.getId(),
            schedule.getClassRoom().getId(),
            schedule.getClassRoom().getName(),
            schedule.getYear(),
            schedule.getSemester(),
            schedule.getExamType(),
            schedule.getStartDate(),
            schedule.getEndDate(),
            schedule.getScopeNote());
    }
}
