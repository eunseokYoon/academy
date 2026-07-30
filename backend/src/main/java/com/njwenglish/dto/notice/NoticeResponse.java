package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import com.njwenglish.entity.enums.NoticeScope;
import java.time.OffsetDateTime;

/**
 * T-10 선생님 목록·상세. <b>초안까지 전부 보인다</b> —
 * publishedAt이 null이면 아직 학생·학부모에게 안 나간 글이다.
 */
public record NoticeResponse(
    Long noticeId,
    String title,
    String content,
    NoticeScope scope,
    Long classRoomId,
    String classRoomName,
    boolean pinned,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
            notice.getId(), notice.getTitle(), notice.getContent(), notice.getScope(),
            notice.getClassRoom() == null ? null : notice.getClassRoom().getId(),
            notice.getClassRoom() == null ? null : notice.getClassRoom().getName(),
            notice.isPinned(), notice.getPublishedAt(), notice.getCreatedAt());
    }
}
