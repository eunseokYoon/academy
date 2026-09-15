package com.njwenglish.dto.review;

import jakarta.validation.constraints.NotBlank;

/** 별점은 0.5~5.0의 실수다. 저장값(1~10)이 API 밖으로 나가지 않는다. */
public record CourseReviewSaveRequest(double rating, @NotBlank String content) {
}
