package com.njwenglish.dto.push;

import com.njwenglish.entity.enums.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** FCM 토큰은 보통 160~200자다. 상한은 쓰레기 값을 막는 용도다. */
public record DeviceTokenRequest(
    @NotBlank @Size(max = 4096) String token,
    @NotNull DevicePlatform platform
) {
}
