package com.njwenglish.dto.homework;

/**
 * S-4. 피드백은 여기에만 나온다. 학부모 응답에는 이 필드가 없다.
 *
 * <p>resubmitRequired는 제출 UI(사진·영상 추가, 제출 버튼)를 여는 <b>유일한 근거</b>다.
 * homework.kind·submission.status·homework.dueAt만으로는 판정할 수 없어 서버가 따로 내려준다.
 */
public record StudentHomeworkDetailResponse(StudentHomeworkResponse homework,
                                            StudentSubmissionResponse submission,
                                            boolean resubmitRequired,
                                            FeedbackResponse feedback) {
}
