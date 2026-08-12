package com.njwenglish.dto.attendance;

import java.util.List;

/**
 * S-6 · P-2 월별 캘린더. days에는 수업이 있는 날만 담는다 —
 * 없는 날은 프론트가 빈 칸으로 그린다.
 */
public record AttendanceCalendarResponse(int year,
                                         int month,
                                         AttendanceSummaryResponse summary,
                                         Integer homeworkCompletionRate,
                                         List<AttendanceDayResponse> days) {
}
