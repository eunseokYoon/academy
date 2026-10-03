package com.njwenglish.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** 계정 삭제는 되돌릴 수 없어서 비밀번호를 한 번 더 받는다. */
public record AccountDeleteRequest(@NotBlank String password) {
}
