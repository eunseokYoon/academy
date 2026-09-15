package com.njwenglish.dto.notice;

import com.njwenglish.entity.enums.NoticeAudience;
import com.njwenglish.entity.enums.NoticeScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * scope별 필수 필드를 서버에서 검증한다. {@code CLASS}면 classRoomId가 있어야 하고,
 * {@code ALL}이면 없어야 한다. 어긋나면 400이고, 통과시켜도 ck_notices_target이 막는다.
 *
 * <p>여러 반에 같은 공지를 내리려면 <b>반마다 이 요청을 보낸다.</b>
 *
 * <p>content는 일반 텍스트다. 마크다운·리치 텍스트를 지원하지 마라 — 렌더링을 붙이는
 * 순간 XSS 처리가 필요해진다. 줄바꿈 유지까지가 범위다.
 *
 * <p>만들어진 공지는 <b>초안</b>이다. publish를 따로 호출해야 학생·학부모에게 보인다.
 *
 * <p>audience가 대상이다. ALL은 학생·학부모 둘 다, STUDENT_ONLY는 학부모에게 안 보이고,
 * PARENT_ONLY는 학생에게 안 보인다. <b>불리언으로 되돌리지 마라</b> — 둘 다 켜져
 * 아무도 못 보는 공지가 만들어진다.
 *
 * <p>attachments는 최대 5개다. 각 s3Key는 {@code POST /teacher/notices/attachments/upload-url}로
 * 발급받아 서버에 서명을 대조받는다 — 그대로 믿으면 버킷 내 임의 경로를 첨부로 등록할 수 있다.
 */
public record NoticeCreateRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotNull NoticeScope scope,
    Long classRoomId,
    boolean pinned,
    @NotNull NoticeAudience audience,
    List<NoticeAttachmentRequest> attachments
) {
}
