package com.njwenglish.dto.review;

import com.njwenglish.common.response.PageResponse;

/**
 * averageRating은 후기가 없으면 null이다. 0.0으로 접지 마라.
 *
 * <p>totalCount는 reviews.totalElements()와 같은 값이다. 헤더(평균 옆)에서
 * 쓰려고 꺼내 둔 것이니, 둘이 어긋나게 계산하지 마라.
 */
public record CourseReviewListResponse(
    Double averageRating, long totalCount,
    PageResponse<TeacherCourseReviewResponse> reviews) {
}
