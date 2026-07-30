package com.njwenglish.dto.homework;

/** S-4. 피드백은 여기에만 나온다. 학부모 응답에는 이 필드가 없다. */
public record StudentHomeworkDetailResponse(StudentHomeworkResponse homework,
                                            StudentSubmissionResponse submission,
                                            FeedbackResponse feedback) {
}
