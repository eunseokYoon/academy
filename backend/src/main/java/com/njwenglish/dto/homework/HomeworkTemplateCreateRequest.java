package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HomeworkTemplateCreateRequest(@NotBlank @Size(max = 200) String title,
                                            String description) {
}
