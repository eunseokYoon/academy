package com.njwenglish.dto.push;

import jakarta.validation.constraints.NotNull;

public record PushSettingRequest(@NotNull Boolean enabled) {
}
