package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.homework.FeedbackCreateRequest;
import com.njwenglish.dto.homework.FeedbackResponse;
import com.njwenglish.dto.homework.SubmissionDetailResponse;
import com.njwenglish.service.FeedbackService;
import com.njwenglish.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-7 상세 뷰어와 피드백.
 *
 * <p>"저장하고 다음"이 이 화면의 전부다. 상세 응답의 nextSubmissionId로 목록에 돌아가지 않고
 * 다음 미확인 제출물로 넘어간다.
 */
@RestController
@RequestMapping("/api/teacher/submissions")
@RequiredArgsConstructor
public class TeacherSubmissionController {

    private final SubmissionService submissionService;
    private final FeedbackService feedbackService;

    @GetMapping("/{submissionId}")
    public ApiResponse<SubmissionDetailResponse> detail(@PathVariable Long submissionId) {
        return ApiResponse.ok(submissionService.detail(submissionId));
    }

    @PostMapping("/{submissionId}/feedback")
    public ApiResponse<FeedbackResponse> createFeedback(
        @PathVariable Long submissionId,
        @Valid @RequestBody FeedbackCreateRequest request) {
        return ApiResponse.ok(feedbackService.create(submissionId, request.content()));
    }

    @PatchMapping("/{submissionId}/feedback")
    public ApiResponse<FeedbackResponse> updateFeedback(
        @PathVariable Long submissionId,
        @Valid @RequestBody FeedbackCreateRequest request) {
        return ApiResponse.ok(feedbackService.update(submissionId, request.content()));
    }

    /** 피드백 없이 확인만 하고 넘어간다. */
    @PostMapping("/{submissionId}/check")
    public ApiResponse<Void> check(@PathVariable Long submissionId) {
        feedbackService.check(submissionId);
        return ApiResponse.ok(null);
    }
}
