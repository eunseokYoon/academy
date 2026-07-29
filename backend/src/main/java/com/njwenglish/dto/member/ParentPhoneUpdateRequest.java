package com.njwenglish.dto.member;

import jakarta.validation.constraints.NotBlank;

/** 번호를 바꾸면 로그인 아이디도 함께 바뀐다. 다음 로그인부터 새 번호를 쓴다. */
public record ParentPhoneUpdateRequest(@NotBlank String phone) {
}
