package com.njwenglish.dto.lessonchange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * S-9 수업일 변경 요청.
 *
 * <p>reason은 필수다. <b>승인 공지 본문에 그대로 들어간다</b> — 학부모가 읽을 문장이라
 * 빈 값이면 "수업일이 바뀌었다"는 사실만 남고 왜 바뀌었는지가 사라진다.
 *
 * <p>클리닉 변경과 달리 선택지 코드가 없다. 옵션 목록이 미확정이라 지어내지 않는다.
 */
public record LessonChangeCreateRequest(
    @NotNull Long fromLessonId,
    @NotNull Long toLessonId,
    @NotBlank @Size(max = 500) String reason
) {
}
