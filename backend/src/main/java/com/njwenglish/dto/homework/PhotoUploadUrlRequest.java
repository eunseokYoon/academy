package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** bytes는 리사이즈 <b>후</b> 크기다. 원본을 그대로 올리면 연 200GB가 넘는다. */
public record PhotoUploadUrlRequest(@NotBlank String contentType,
                                    @NotNull @Positive Integer bytes) {
}
