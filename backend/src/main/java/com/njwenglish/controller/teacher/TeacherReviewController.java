package com.njwenglish.controller.teacher;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.review.CourseReviewListResponse;
import com.njwenglish.service.CourseReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T-15 수강 후기 탭. <b>읽기만 있다.</b> 답글도 상태 변경도 없다 —
 * 강사가 1명이라 200명분 상태를 닫는 절차가 그대로 병목이 된다.
 */
@RestController
@RequestMapping("/api/teacher/reviews")
@RequiredArgsConstructor
public class TeacherReviewController {

    private final CourseReviewService reviewService;

    @GetMapping
    public ApiResponse<CourseReviewListResponse> list(
        @RequestParam(required = false) Long classRoomId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(reviewService.listForTeacher(classRoomId, pageable));
    }
}
