package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.score.ExamScheduleCreateRequest;
import com.njwenglish.dto.score.ExamScheduleResponse;
import com.njwenglish.dto.score.ExamScheduleUpdateRequest;
import com.njwenglish.service.ExamScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * T-11. 반마다 한 행이라 반이 3개면 3번 호출한다.
 * 화면에서 반을 다중 선택해 한 번의 저장으로 보내라 — 한 반이 빠지면 그 반 D-day가 비어 있다.
 */
@RestController
@RequestMapping("/api/teacher/exam-schedules")
@RequiredArgsConstructor
public class TeacherExamScheduleController {

    private final ExamScheduleService examScheduleService;

    @GetMapping
    public ApiResponse<List<ExamScheduleResponse>> list(
        @RequestParam(required = false) Long classRoomId,
        @RequestParam(required = false) Short year) {
        return ApiResponse.ok(examScheduleService.list(classRoomId, year));
    }

    @PostMapping
    public ApiResponse<ExamScheduleResponse> create(
        @Valid @RequestBody ExamScheduleCreateRequest request) {
        return ApiResponse.ok(examScheduleService.create(request));
    }

    @PatchMapping("/{examScheduleId}")
    public ApiResponse<ExamScheduleResponse> update(
        @PathVariable Long examScheduleId,
        @RequestBody ExamScheduleUpdateRequest request) {
        return ApiResponse.ok(examScheduleService.update(examScheduleId, request));
    }

    @DeleteMapping("/{examScheduleId}")
    public ApiResponse<Void> delete(@PathVariable Long examScheduleId) {
        examScheduleService.delete(examScheduleId);
        return ApiResponse.ok();
    }
}
