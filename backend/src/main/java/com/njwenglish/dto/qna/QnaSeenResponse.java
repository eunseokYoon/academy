package com.njwenglish.dto.qna;

import java.time.OffsetDateTime;

/** 선생님이 게시판을 열기 직전의 「마지막으로 본 시각」. 이 뒤의 질문이 새 글이다. */
public record QnaSeenResponse(OffsetDateTime previousSeenAt) {
}
