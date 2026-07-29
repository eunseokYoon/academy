package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceConfirmResponse;
import com.njwenglish.dto.attendance.AttendanceCorrectRequest;
import com.njwenglish.dto.attendance.AttendanceDetailResponse;
import com.njwenglish.dto.attendance.AttendanceRosterResponse;
import com.njwenglish.dto.attendance.PendingLessonResponse;
import com.njwenglish.service.AttendanceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-5. 경로 접두사가 /lessons, /attendances, /attendance 셋으로 갈려 있어
 * 클래스는 /api/teacher에 매핑한다.
 *
 * <p>확정 전 중간 입력을 저장하는 API는 없다. 프론트 로컬 상태로만 유지한다 —
 * attendances에 미리 쓰면 캘린더의 확정 판정이 무너진다.
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/lessons/{lessonId}/attendance")
    public ApiResponse<AttendanceRosterResponse> roster(@PathVariable Long lessonId) {
        return ApiResponse.ok(attendanceService.roster(lessonId));
    }

    /** 안 온 학생만 보낸다. 빈 배열이면 전원 출석으로 확정된다. */
    @PostMapping("/lessons/{lessonId}/attendance/confirm")
    public ApiResponse<AttendanceConfirmResponse> confirm(
        @PathVariable Long lessonId,
        @Valid @RequestBody AttendanceConfirmRequest request) {
        return ApiResponse.ok(attendanceService.confirm(lessonId, request));
    }

    @PatchMapping("/attendances/{attendanceId}")
    public ApiResponse<AttendanceDetailResponse> correct(
        @PathVariable Long attendanceId,
        @Valid @RequestBody AttendanceCorrectRequest request) {
        return ApiResponse.ok(attendanceService.correct(attendanceId, request));
    }

    @GetMapping("/attendance/pending")
    public ApiResponse<List<PendingLessonResponse>> pending() {
        return ApiResponse.ok(attendanceService.pending());
    }
}
