package com.njwenglish.controller.student;

import com.njwenglish.common.response.ApiResponse;
import com.njwenglish.dto.review.CourseReviewSaveRequest;
import com.njwenglish.dto.review.MyCourseReviewResponse;
import com.njwenglish.service.CourseReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 수강 후기. <b>학생당 하나</b>라 경로에 id가 없다 — 항상 "내 후기"다.
 *
 * <p>studentId를 받지 않는다. 받는 순간 requireAccessible을 붙일 자리가 생기고,
 * 그건 학부모가 자녀 후기를 볼 수 있다는 뜻이 된다. 후기는 선생님만 읽는다.
 */
@RestController
@RequestMapping("/api/student/reviews")
@RequiredArgsConstructor
public class StudentReviewController {

    private final CourseReviewService reviewService;

    /** 없으면 data가 null이다. 404가 아니다 — 버튼 상태를 정하는 호출이다. */
    @GetMapping("/me")
    public ApiResponse<MyCourseReviewResponse> myReview() {
        return ApiResponse.ok(reviewService.myReview());
    }

    @PostMapping
    public ApiResponse<MyCourseReviewResponse> create(
        @Valid @RequestBody CourseReviewSaveRequest request) {
        return ApiResponse.ok(reviewService.save(request, true));
    }

    @PatchMapping("/me")
    public ApiResponse<MyCourseReviewResponse> update(
        @Valid @RequestBody CourseReviewSaveRequest request) {
        return ApiResponse.ok(reviewService.save(request, false));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> delete() {
        reviewService.delete();
        return ApiResponse.ok();
    }
}
