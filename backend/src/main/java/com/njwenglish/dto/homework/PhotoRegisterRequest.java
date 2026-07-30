package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** s3Key는 서버가 발급한 것이어야 한다. 서명이 제출물에 묶여 있어 남의 키는 통과하지 못한다. */
public record PhotoRegisterRequest(@NotBlank String s3Key,
                                   @NotNull @Positive Short sortOrder,
                                   Integer bytes) {
}
