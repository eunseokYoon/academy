package com.njwenglish.dto.attendance;

import com.njwenglish.entity.enums.AttendanceStatus;
import java.util.Collection;

/** 상태별 집계. 확정된(CONFIRMED) 것만 넣는다. */
public record AttendanceSummaryResponse(int present, int late, int absent, int sick,
                                       int excused, int makeup,
                                       /** 온라인(2026-09-29). 출석으로 친다 — 화면의 「출석」 칸은 present + makeup + online 이다. */
                                       int online) {

    public static AttendanceSummaryResponse of(Collection<AttendanceStatus> statuses) {
        int present = 0;
        int late = 0;
        int absent = 0;
        int sick = 0;
        int excused = 0;
        int makeup = 0;
        int online = 0;
        for (AttendanceStatus status : statuses) {
            switch (status) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
                case SICK -> sick++;
                case EXCUSED -> excused++;
                case MAKEUP -> makeup++;
                case ONLINE -> online++;
            }
        }
        return new AttendanceSummaryResponse(present, late, absent, sick, excused, makeup,
            online);
    }
}
