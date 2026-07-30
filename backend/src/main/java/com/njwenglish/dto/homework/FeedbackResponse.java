package com.njwenglish.dto.homework;

import com.njwenglish.entity.Feedback;
import java.time.OffsetDateTime;

/** 학생(S-4)과 선생님(T-7)에게만 나간다. <b>학부모 응답에 넣지 마라.</b> */
public record FeedbackResponse(String content, OffsetDateTime createdAt) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getContent(), feedback.getCreatedAt());
    }
}
