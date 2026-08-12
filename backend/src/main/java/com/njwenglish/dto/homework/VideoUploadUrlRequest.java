package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 영상은 브라우저에서 압축할 방법이 없어 폰이 찍은 원본이 그대로 올라온다.
 * bytes로만 막는다 — 서버는 재생 길이를 잴 수 없다.
 */
public record VideoUploadUrlRequest(@NotBlank String contentType,
                                    @NotNull @Positive Integer bytes) {
}
