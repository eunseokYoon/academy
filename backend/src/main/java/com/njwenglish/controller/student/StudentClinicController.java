package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.clinic.ClinicSeriesResponse;
import com.njwenglish.dto.clinic.ClinicSeriesReserveResponse;
import com.njwenglish.dto.clinic.ClinicSeriesReserveRequest;
import com.njwenglish.dto.clinic.ClinicReservationChangeRequest;
import com.njwenglish.dto.clinic.ClinicReservationCreateRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/clinics/{clinicId}/reservation")
    public ApiResponse<ClinicReservationCreateResponse> reserve(
        @PathVariable Long clinicId,
        @Valid @RequestBody ClinicReservationCreateRequest request) {
        return ApiResponse.ok(clinicReservationService.reserve(clinicId, request));
    }

    /** 도착 시각 변경 · 다른 클리닉으로 이동. 즉시 반영되고 사유가 남는다. */
    @PatchMapping("/clinics/{clinicId}/reservation")
    public ApiResponse<ClinicReservationCreateResponse> change(
        @PathVariable Long clinicId,
        @Valid @RequestBody ClinicReservationChangeRequest request) {
        return ApiResponse.ok(clinicReservationService.change(clinicId, request));
    }

    /**
     * 시리즈 카드. 「매주 화요일 17:00~22:00 · 총 18회」 단위로 묶어 내려준다.
     * 시리즈에 id가 없어서 신청은 이 응답의 (요일·시작·종료)를 그대로 되돌려 보낸다.
     */
    @GetMapping("/clinics/series")
    public ApiResponse<List<ClinicSeriesResponse>> series() {
        return ApiResponse.ok(clinicReservationService.series());
    }

    /** 시리즈 일괄 신청. 정원이 찬 회차만 빠지고 나머지는 신청된다. */
    @PostMapping("/clinics/series/reservations")
    public ApiResponse<ClinicSeriesReserveResponse> reserveSeries(
        @Valid @RequestBody ClinicSeriesReserveRequest request) {
        return ApiResponse.ok(clinicReservationService.reserveSeries(request));
    }
}
