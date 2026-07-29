package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S-6. 학부모 응답과 형식이 같고, 대상이 본인으로 고정된다. */
@RestController
@RequestMapping("/api/student/attendances")
@RequiredArgsConstructor
public class StudentAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    public ApiResponse<AttendanceCalendarResponse> calendar(@RequestParam int year,
                                                            @RequestParam int month) {
        return ApiResponse.ok(attendanceService.myCalendar(year, month));
    }
}
