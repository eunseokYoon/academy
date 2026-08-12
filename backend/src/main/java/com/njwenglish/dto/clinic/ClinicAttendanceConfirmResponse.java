package com.njwenglish.dto.clinic;

import com.njwenglish.dto.attendance.AttendanceSummaryResponse;
import java.time.OffsetDateTime;

public record ClinicAttendanceConfirmResponse(Long clinicId,
                                              OffsetDateTime confirmedAt,
                                              AttendanceSummaryResponse summary) {
}
