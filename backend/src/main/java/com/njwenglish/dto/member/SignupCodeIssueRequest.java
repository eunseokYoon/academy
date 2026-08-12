package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.UserRole;
import jakarta.validation.constraints.NotNull;

/** phone을 함께 주면 대조 번호도 바꾼다(번호를 잘못 입력한 경우). 생략하면 기존 번호를 유지한다. */
public record SignupCodeIssueRequest(@NotNull UserRole target, String phone) {
}
