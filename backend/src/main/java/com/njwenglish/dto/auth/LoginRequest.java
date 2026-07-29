package com.njwenglish.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** loginId는 전화번호다. 하이픈이 섞여 들어오므로 서버에서 다시 정규화한다. */
public record LoginRequest(
    @NotBlank String loginId,
    @NotBlank String password
) {
}
