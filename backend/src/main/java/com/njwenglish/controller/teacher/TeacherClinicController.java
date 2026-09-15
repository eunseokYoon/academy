package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.clinic.ClinicAssignRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmRequest;
import com.njwenglish.dto.clinic.ClinicAttendanceConfirmResponse;
import com.njwenglish.dto.clinic.ClinicBulkCreateRequest;
import com.njwenglish.dto.clinic.ClinicBulkCreateResponse;
import com.njwenglish.dto.clinic.ClinicCreateRequest;
import com.njwenglish.dto.clinic.ClinicCreateResponse;
import com.njwenglish.dto.clinic.ClinicListItemResponse;
import com.njwenglish.dto.clinic.ClinicReservationListResponse;
import com.njwenglish.dto.clinic.ClinicUpdateRequest;
import com.njwenglish.entity.enums.ClinicStatus;
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

/**
 * T-13 클리닉 시간대 관리.
 *
 * <p><b>변경 승인도, 변경 목록도 없다</b>(2026-08-10 · 08-11 확정). 학생이 바꾸면 즉시
 * 반영되고 학생·학부모에게 공지가 나간다. 선생님은 명단에서 결과만 본다 —
 * 승인·거절이나 변경 이력 화면을 다시 만들지 마라.
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherClinicController {

    private final ClinicService clinicService;
    private final ClinicReservationService clinicReservationService;

    /**
     * T-13 목록. <b>주차 단위다</b>(2026-08-11 확정) — 날짜 범위를 손으로 넣던 것을 바꿨다.
     * 성적·온라인 테스트 화면과 같은 년·월·주차 선택을 쓴다.
     */
    @GetMapping("/clinics")
    public ApiResponse<List<ClinicListItemResponse>> list(
        @RequestParam int year,
        @RequestParam int month,
        @RequestParam int week,
        @RequestParam(required = false) ClinicStatus status) {
        return ApiResponse.ok(clinicService.listForTeacher(year, month, week, status));
    }

    /** 기간 안의 특정 요일에 한꺼번에 연다. 충돌하는 날짜는 건너뛰고 몇 건 만들었는지 돌려준다. */
    // 클래스 매핑이 /api/teacher다. "/bulk"만 쓰면 /api/teacher/bulk가 되고,
    // 프론트가 부르는 /api/teacher/clinics/bulk는 PATCH·DELETE /clinics/{clinicId}에
    // clinicId="bulk"로 매칭돼 POST만 없는 상태가 된다 — 404가 아니라 405로 나온다
    @PostMapping("/clinics/bulk")
    public ApiResponse<ClinicBulkCreateResponse> bulkCreate(
        @Valid @RequestBody ClinicBulkCreateRequest request) {
        return ApiResponse.ok(clinicService.bulkCreate(request));
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

    /** T-5와 같은 방식이다. 안 온 학생만 보낸다. 단, <b>도착 시각 슬롯 하나</b>만 확정한다. */
    @PostMapping("/clinics/{clinicId}/attendance/confirm")
    public ApiResponse<ClinicAttendanceConfirmResponse> confirmAttendance(
        @PathVariable Long clinicId,
        @Valid @RequestBody ClinicAttendanceConfirmRequest request) {
        return ApiResponse.ok(clinicReservationService.confirmAttendance(clinicId, request));
    }
}
