package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import java.time.OffsetDateTime;

/**
 * 학생·학부모 목록. 본문은 상세에서 받는다.
 * 여기 오는 공지는 전부 발행된 것이라 publishedAt이 null일 수 없다.
 */
public record NoticeSummaryResponse(
    Long noticeId,
    String title,
    boolean pinned,
    OffsetDateTime publishedAt
) {
    public static NoticeSummaryResponse from(Notice notice) {
        return new NoticeSummaryResponse(
            notice.getId(), notice.getTitle(), notice.isPinned(), notice.getPublishedAt());
    }
}
