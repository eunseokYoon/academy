package com.njwenglish.dto.review;

import com.njwenglish.common.util.Ratings;
import com.njwenglish.entity.CourseReview;
import java.time.OffsetDateTime;

/** 내 후기. 없으면 응답이 통째로 null이다 — 404가 아니다. */
public record MyCourseReviewResponse(
    Long reviewId, double rating, String content, OffsetDateTime updatedAt) {

    public static MyCourseReviewResponse from(CourseReview review) {
        return new MyCourseReviewResponse(review.getId(),
            Ratings.toDisplay(review.getRating()), review.getContent(),
            review.getUpdatedAt());
    }
}
