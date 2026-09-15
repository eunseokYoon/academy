package com.njwenglish.dto.notice;

import com.njwenglish.entity.Notice;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 학생·학부모 상세. <b>content를 HTML로 렌더링하지 마라.</b>
 * 사용자가 입력한 텍스트다. 프론트는 줄바꿈만 유지해 일반 텍스트로 출력한다.
 *
 * <p>작성자·scope는 내려주지 않는다. 학생·학부모에게는 필요 없는 정보다.
 *
 * <p>attachments는 엔티티에서 바로 꺼낼 수 없다 — 별도 조회라 {@link #from}이
 * 인자로 받는다.
 */
public record NoticeDetailResponse(
    Long noticeId,
    String title,
    String content,
    boolean pinned,
    OffsetDateTime publishedAt,
    List<NoticeAttachmentResponse> attachments
) {
    public static NoticeDetailResponse from(Notice notice,
                                            List<NoticeAttachmentResponse> attachments) {
        return new NoticeDetailResponse(
            notice.getId(), notice.getTitle(), notice.getContent(),
            notice.isPinned(), notice.getPublishedAt(), attachments);
    }
}
