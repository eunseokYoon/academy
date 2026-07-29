package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.clinic.ClinicAssignRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmResponse;
import com.njwenglish.dto.clinic.ClinicChangeRequestResponse;
import com.njwenglish.dto.clinic.ClinicCreateRequest;
import com.njwenglish.dto.clinic.ClinicCreateResponse;
import com.njwenglish.dto.clinic.ClinicDecideRequest;
import com.njwenglish.dto.clinic.ClinicListItemResponse;
import com.njwenglish.dto.clinic.ClinicReservationListResponse;
import com.njwenglish.dto.clinic.ClinicUpdateRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import com.njwenglish.entity.enums.ClinicStatus;
import com.njwenglish.service.ClinicChangeRequestService;
import com.njwenglish.service.ClinicReservationService;
import com.njwenglish.service.ClinicService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** T-13. /clinics와 /clinic-change-requests 두 접두사를 함께 다룬다. */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherClinicController {

    private final ClinicService clinicService;
    private final ClinicReservationService clinicReservationService;
    private final ClinicChangeRequestService clinicChangeRequestService;

    @GetMapping("/clinics")
    public ApiResponse<List<ClinicListItemResponse>> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) ClinicStatus status) {
        return ApiResponse.ok(clinicService.listForTeacher(from, to, status));
    }

    @PostMapping("/clinics")
    public ApiResponse<ClinicCreateResponse> create(
        @Valid @RequestBody ClinicCreateRequest request) {
        return ApiResponse.ok(clinicService.create(request));
    }

    @PatchMapping("/clinics/{clinicId}")
    public ApiResponse<ClinicListItemResponse> update(@PathVariable Long clinicId,
                                                      @RequestBody ClinicUpdateRequest request) {
        return ApiResponse.ok(clinicService.update(clinicId, request));
    }

    @DeleteMapping("/clinics/{clinicId}")
    public ApiResponse<Void> delete(@PathVariable Long clinicId) {
        clinicService.delete(clinicId);
        return ApiResponse.ok();
    }

    @GetMapping("/clinics/{clinicId}/reservations")
    public ApiResponse<ClinicReservationListResponse> reservations(@PathVariable Long clinicId) {
        return ApiResponse.ok(clinicReservationService.reservations(clinicId));
    }

    @PostMapping("/clinics/{clinicId}/students")
    public ApiResponse<ClinicReservationListResponse> assign(
        @PathVariable Long clinicId,
        @Valid @RequestBody ClinicAssignRequest request) {
        return ApiResponse.ok(clinicReservationService.assign(clinicId, request));
    }

    @DeleteMapping("/clinics/{clinicId}/students/{studentId}")
    public ApiResponse<Void> unassign(@PathVariable Long clinicId,
                                      @PathVariable Long studentId) {
        clinicReservationService.unassign(clinicId, studentId);
        return ApiResponse.ok();
    }

    /** T-5와 같은 방식이다. 안 온 학생만 보낸다. */
    @PostMapping("/clinics/{clinicId}/attendance/confirm")
    public ApiResponse<ClinicAttendanceConfirmResponse> confirmAttendance(
        @PathVariable Long clinicId,
        @Valid @RequestBody AttendanceConfirmRequest request) {
        return ApiResponse.ok(clinicReservationService.confirmAttendance(clinicId, request));
    }

    @GetMapping("/clinic-change-requests")
    public ApiResponse<List<ClinicChangeRequestResponse>> changeRequests(
        @RequestParam(required = false, defaultValue = "PENDING") ChangeRequestStatus status) {
        return ApiResponse.ok(clinicChangeRequestService.list(status));
    }

    @PostMapping("/clinic-change-requests/{requestId}/decide")
    public ApiResponse<ClinicChangeRequestResponse> decide(
        @PathVariable Long requestId,
        @Valid @RequestBody ClinicDecideRequest request) {
        return ApiResponse.ok(clinicChangeRequestService.decide(requestId, request));
    }
}
