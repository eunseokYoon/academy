package com.njwenglish.dto.qna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QnaUploadUrlRequest(
    @NotBlank String contentType,
    @NotNull Integer bytes) {
}
