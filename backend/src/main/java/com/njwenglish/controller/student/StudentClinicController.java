package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.clinic.ClinicChangeRequestCreateRequest;
import com.njwenglish.dto.clinic.ClinicChangeRequestCreateResponse;
import com.njwenglish.dto.clinic.ClinicReservationCreateResponse;
import com.njwenglish.dto.clinic.StudentClinicResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S-9. 응답에 다른 학생 이름이 들어가지 않는다 — 인원 수만이다. */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentClinicController {

    private final ClinicService clinicService;
    private final ClinicReservationService clinicReservationService;
    private final ClinicChangeRequestService clinicChangeRequestService;

    @GetMapping("/clinics")
    public ApiResponse<List<StudentClinicResponse>> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(clinicService.listForStudent(from, to));
    }

    @PostMapping("/clinics/{clinicId}/reservation")
    public ApiResponse<ClinicReservationCreateResponse> reserve(@PathVariable Long clinicId) {
        return ApiResponse.ok(clinicReservationService.reserve(clinicId));
    }

    @DeleteMapping("/clinics/{clinicId}/reservation")
    public ApiResponse<Void> cancel(@PathVariable Long clinicId) {
        clinicReservationService.cancel(clinicId);
        return ApiResponse.ok();
    }

    /** 학생이 직접 시간을 옮기지 못한다. 요청하면 선생님이 승인한다. */
    @PostMapping("/clinic-change-requests")
    public ApiResponse<ClinicChangeRequestCreateResponse> requestChange(
        @Valid @RequestBody ClinicChangeRequestCreateRequest request) {
        return ApiResponse.ok(clinicChangeRequestService.create(request));
    }
}
