package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.clinic.ClinicReservationChangeRequest;
import com.njwenglish.dto.clinic.ClinicReservationCreateResponse;
import com.njwenglish.dto.clinic.StudentClinicResponse;
import com.njwenglish.service.ClinicReservationService;
import com.njwenglish.service.ClinicService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S-9. 응답에 다른 학생 이름이 들어가지 않는다 — 인원 수만이다.
 *
 * <p><b>변경에 선생님 승인이 없다</b>(2026-08-10 확정). 대신 사유가 필수고,
 * 변경하면 <b>공지가 한 건 발행되어</b> 학생·학부모의 공지 탭에 뜬다(수업일 변경과 같은 경로).
 * 승인 엔드포인트를 다시 만들지 마라.
 *
 * <p><b>학생 취소 엔드포인트는 없다</b>(2026-08-10 확정). 못 가면 다른 시각으로 옮기고,
 * 아예 빠져야 하면 선생님이 T-13에서 배정 해제한다. 다시 만들지 마라.
 *
 * <p><b>학생은 클리닉을 신청하지 못한다</b>(2026-09-01 확정). 배정은 선생님이 T-13에서 한다.
 * 신청 엔드포인트를 되살리지 마라 — 화면에서 버튼을 안 그리는 것만으로는 막히지 않는다.
 * 남은 것은 목록 조회와 <b>변경</b>뿐이고, 변경은 배정받은 예약이 있어야만 동작한다.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentClinicController {

    private final ClinicService clinicService;
    private final ClinicReservationService clinicReservationService;

    @GetMapping("/clinics")
    public ApiResponse<List<StudentClinicResponse>> list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(clinicService.listForStudent(from, to));
    }

    /** 도착 시각 변경 · 다른 클리닉으로 이동. 즉시 반영되고 사유가 남는다. */
    @PatchMapping("/clinics/{clinicId}/reservation")
    public ApiResponse<ClinicReservationCreateResponse> change(
        @PathVariable Long clinicId,
        @Valid @RequestBody ClinicReservationChangeRequest request) {
        return ApiResponse.ok(clinicReservationService.change(clinicId, request));
    }
}
