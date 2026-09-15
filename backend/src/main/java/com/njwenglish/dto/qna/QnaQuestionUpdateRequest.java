package com.njwenglish.dto.qna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 질문·답글 공통 수정 요청. <b>대상이 답글인데 title이나 isPublic이 오면 400이다</b> —
 * 답글에는 그 개념이 없고, 통과시켜도 ck_qna_posts_shape가 막는다.
 *
 * <p>사진은 수정 대상이 아니다. 바꾸려면 지우고 다시 쓴다.
 */
public record QnaQuestionUpdateRequest(
    @Size(max = 200) String title,
    @NotBlank String content,
    Boolean isPublic) {
}
