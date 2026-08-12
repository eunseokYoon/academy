package com.njwenglish.dto.onlinetest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 해설지 업로드 URL 발급. 출제 화면에서 파일을 먼저 올리고 받은 s3Key로 테스트를 만든다.
 * 서버는 바이트를 다루지 않는다 — 클라이언트가 S3로 직접 PUT한다.
 */
public record AnswerUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull Long bytes
) {
}
