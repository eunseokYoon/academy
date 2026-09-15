package com.njwenglish.dto.review;

import com.njwenglish.common.util.Ratings;
import com.njwenglish.entity.CourseReview;
import java.time.OffsetDateTime;

/**
 * T-15 후기 카드. <b>선생님만 본다.</b> 학생·학부모 응답에 이 타입이 들어가면 안 된다.
 *
 * <p>작성자 이름은 students.name이다 — users.name이 아니다.
 * 미가입 학생은 users 행이 없다.
 */
public record TeacherCourseReviewResponse(
    Long reviewId, double rating, String content,
    String studentName, String classRoomName, OffsetDateTime createdAt) {

    public static TeacherCourseReviewResponse from(CourseReview review) {
        return new TeacherCourseReviewResponse(review.getId(),
            Ratings.toDisplay(review.getRating()), review.getContent(),
            review.getStudent().getName(),
            review.getClassRoom() == null ? null : review.getClassRoom().getName(),
            review.getCreatedAt());
    }
}
