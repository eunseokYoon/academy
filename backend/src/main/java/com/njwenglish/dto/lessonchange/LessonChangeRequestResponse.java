package com.njwenglish.dto.lessonchange;

import com.njwenglish.entity.LessonChangeRequest;
import com.njwenglish.entity.enums.ChangeRequestStatus;
import java.time.OffsetDateTime;

/**
 * 학생 목록(S-9)과 선생님 대기 목록(T-13)이 같은 모양을 쓴다.
 *
 * <p>studentName은 선생님 화면에서만 의미가 있지만, 학생이 보는 것도 자기 이름이라
 * 숨길 이유가 없다. <b>students.name이다</b> — users.name을 타면 미가입 학생이 사라진다.
 */
public record LessonChangeRequestResponse(
    Long requestId,
    Long studentId,
    String studentName,
    LessonSlotResponse from,
    LessonSlotResponse to,
    String reason,
    ChangeRequestStatus status,
    OffsetDateTime decidedAt,
    OffsetDateTime createdAt
) {
    public static LessonChangeRequestResponse from(LessonChangeRequest request) {
        return new LessonChangeRequestResponse(
            request.getId(),
            request.getStudent().getId(),
            request.getStudent().getName(),
            LessonSlotResponse.from(request.getFromLesson()),
            LessonSlotResponse.from(request.getToLesson()),
            request.getReason(),
            request.getStatus(),
            request.getDecidedAt(),
            request.getCreatedAt());
    }
}
