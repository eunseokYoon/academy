package com.njwenglish.dto.notice;

import com.njwenglish.entity.enums.NoticeScope;
import java.util.List;

/**
 * null인 필드는 그대로 둔다.
 *
 * <p><b>scope와 classRoomId는 항상 함께 판단한다.</b> scope만 바꾸면 CHECK 제약에 걸린다.
 * scope가 null이면 대상은 건드리지 않는다.
 *
 * <p>pinned는 Boolean이다. boolean으로 두면 "안 보냈다"와 "false로 바꿔라"가 구분되지 않아
 * 제목만 고쳐도 고정이 풀린다.
 *
 * <p>studentsOnly도 같은 이유로 Boolean이다. null이면 기존 값을 그대로 둔다.
 *
 * <p>attachments는 <b>null이면 그대로 두고, 배열이 오면 그 배열로 통째로 교체한다.</b>
 * 빈 배열({@code []})은 "전부 지운다"는 뜻이라 null과 다르다.
 */
public record NoticeUpdateRequest(
    String title,
    String content,
    Boolean pinned,
    NoticeScope scope,
    Long classRoomId,
    Boolean studentsOnly,
    List<NoticeAttachmentRequest> attachments
) {
}
