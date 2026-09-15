package com.njwenglish.dto.qna;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 답글 하나. byTeacher가 true면 선생님 답글이다 — 화면에서 색을 다르게 준다.
 *
 * <p>editable은 "지금 보고 있는 사람이 이 답글을 고칠 수 있는가"다. 화면에서 버튼을 그리는
 * 근거일 뿐이고, 실제 차단은 서버가 다시 한다.
 */
public record QnaAnswerResponse(
    Long answerId,
    String authorName,
    boolean byTeacher,
    String content,
    List<QnaPhotoResponse> photos,
    boolean editable,
    OffsetDateTime createdAt) {
}
