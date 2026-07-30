package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;

public record FeedbackCreateRequest(@NotBlank String content) {
}
