package com.njwenglish.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 정책은 8자 이상뿐이다. 학부모 연령대를 고려해 특수문자 강제 같은 제약은 넣지 않는다. */
public record PasswordChangeRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8) String newPassword
) {
}
