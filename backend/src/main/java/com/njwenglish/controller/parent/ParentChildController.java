package com.njwenglish.controller.parent;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.dto.clinic.ParentClinicResponse;
import com.njwenglish.dto.member.ChildResponse;
import com.njwenglish.service.AttendanceService;
import com.njwenglish.service.ClinicService;
import com.njwenglish.service.ParentService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학부모는 <b>조회만</b> 한다. 클리닉 신청·취소·변경 경로를 여기에 만들지 마라.
 *
 * <p>studentId 소유권은 서비스의 StudentAccessGuard가 검증한다.
 * 컨트롤러에서 따로 검사하지 않는다 — 두 곳에 두면 한 곳을 빠뜨린다.
 */
@RestController
@RequestMapping("/api/parent/children")
@RequiredArgsConstructor
public class ParentChildController {

    private final ParentService parentService;
    private final AttendanceService attendanceService;
    private final ClinicService clinicService;

    @GetMapping
    public ApiResponse<List<ChildResponse>> children() {
        return ApiResponse.ok(parentService.children());
    }

    /** P-2 캘린더. 학생 응답(S-6)과 형식이 같다. */
    @GetMapping("/{studentId}/attendances")
    public ApiResponse<AttendanceCalendarResponse> attendances(@PathVariable Long studentId,
                                                               @RequestParam int year,
                                                               @RequestParam int month) {
        return ApiResponse.ok(attendanceService.calendar(studentId, year, month));
    }

    /** P-2 클리닉. 일정과 출석만 내려간다. */
    @GetMapping("/{studentId}/clinics")
    public ApiResponse<List<ParentClinicResponse>> clinics(
        @PathVariable Long studentId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(clinicService.listForChild(studentId, from, to));
    }
}
