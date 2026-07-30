package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import java.time.OffsetDateTime;

/**
 * 학생·학부모 상세. <b>content를 HTML로 렌더링하지 마라.</b>
 * 사용자가 입력한 텍스트다. 프론트는 줄바꿈만 유지해 일반 텍스트로 출력한다.
 *
 * <p>작성자·scope는 내려주지 않는다. 학생·학부모에게는 필요 없는 정보다.
 */
public record NoticeDetailResponse(
    Long noticeId,
    String title,
    String content,
    boolean pinned,
    OffsetDateTime publishedAt
) {
    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
            notice.getId(), notice.getTitle(), notice.getContent(),
            notice.isPinned(), notice.getPublishedAt());
    }
}
