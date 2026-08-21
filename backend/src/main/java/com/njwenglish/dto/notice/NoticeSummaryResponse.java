package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import java.time.OffsetDateTime;

/**
 * 학생·학부모 목록. 본문은 상세에서 받는다.
 * 여기 오는 공지는 전부 발행된 것이라 publishedAt이 null일 수 없다.
 *
 * <p>hasAttachment만 내려주고 첨부 목록 자체는 담지 않는다 — 목록 한 페이지(20건)마다
 * 첨부를 전부 조회하면 20번의 쿼리가 나간다. 여부만 필요하면 noticeId 묶음으로
 * 한 번에 세는 배치 쿼리로 충분하다.
 */
public record NoticeSummaryResponse(
    Long noticeId,
    String title,
    boolean pinned,
    boolean hasAttachment,
    OffsetDateTime publishedAt
) {
    public static NoticeSummaryResponse from(Notice notice, boolean hasAttachment) {
        return new NoticeSummaryResponse(
            notice.getId(), notice.getTitle(), notice.isPinned(), hasAttachment,
            notice.getPublishedAt());
    }
}
