package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceConfirmResponse;
import com.njwenglish.dto.attendance.AttendanceCorrectRequest;
import com.njwenglish.dto.attendance.AttendanceDetailResponse;
import com.njwenglish.dto.attendance.AttendanceRosterResponse;
import com.njwenglish.dto.attendance.PendingAttendanceResponse;
import com.njwenglish.dto.attendance.WeekAttendanceResponse;
import com.njwenglish.service.AttendanceService;
import com.njwenglish.service.ClinicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ClinicService clinicService;

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

    /**
     * 확정할 것 전부 — 수업과 클리닉을 한 번에 내려준다.
     *
     * <p>두 서비스를 여기서 합치는 이유는 확정하는 대상이 서로 다른 테이블이기 때문이다.
     * 수업은 attendances에, 클리닉은 clinic_reservations.attend_status에 쓴다.
     * 한쪽 서비스가 다른 도메인 리포지토리를 들고 있게 만들지 않는다.
     */
    @GetMapping("/attendance/pending")
    public ApiResponse<PendingAttendanceResponse> pending() {
        return ApiResponse.ok(new PendingAttendanceResponse(
            attendanceService.pending(), clinicService.pendingAttendance()));
    }

    /**
     * 주차 조회. <b>확정된 것도 함께</b> 내려준다(2026-09-01 확정) — 지난 출석을 고치려면
     * 들어갈 입구가 있어야 한다. 미확정 목록(위)은 그대로 둔다. 매일 여는 화면에서
     * "무엇이 남았는지"가 첫 줄이어야 하는 성질을 주차 선택으로 바꾸지 마라.
     *
     * <p>클리닉은 T-13 목록을 그대로 쓴다. 같은 주차·같은 확정 판정이라 새로 만들면
     * 두 화면이 갈라진다.
     */
    @GetMapping("/attendance/week")
    public ApiResponse<WeekAttendanceResponse> week(@RequestParam int year,
                                                    @RequestParam int month,
                                                    @RequestParam int week) {
        return ApiResponse.ok(new WeekAttendanceResponse(
            attendanceService.week(year, month, week),
            clinicService.listForTeacher(year, month, week, null)));
    }
}
