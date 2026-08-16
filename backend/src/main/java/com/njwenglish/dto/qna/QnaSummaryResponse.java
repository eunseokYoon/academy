package com.njwenglish.dto.qna;

import java.time.OffsetDateTime;

/**
 * 목록 카드 한 장. <b>상태 필드가 없다</b> — 답변 완료·미답변 개념을 만들지 않기로 확정했다.
 *
 * <p>authorName은 students.name이다. users.name이 아니다.
 */
public record QnaSummaryResponse(
    Long postId,
    String title,
    String authorName,
    Long classRoomId,
    String classRoomName,
    boolean isPublic,
    boolean mine,
    boolean hasPhoto,
    long answerCount,
    OffsetDateTime createdAt) {
}
