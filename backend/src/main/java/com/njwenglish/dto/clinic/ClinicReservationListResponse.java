package com.njwenglish.dto.clinic;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ClinicReservationListResponse(Long clinicId,
                                            LocalDate clinicDate,
                                            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                            Short capacity,
                                            boolean attendanceConfirmed,
                                            List<ClinicReservationStudentResponse> students) {
}
