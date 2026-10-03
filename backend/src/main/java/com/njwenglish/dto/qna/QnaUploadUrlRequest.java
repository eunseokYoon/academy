package com.njwenglish.dto.qna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** bytes는 리사이즈 <b>후</b> 크기이고 업로드 URL에 서명된다 — 같은 크기로 PUT해야 한다. */
public record QnaUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull @Positive Integer bytes) {
}
