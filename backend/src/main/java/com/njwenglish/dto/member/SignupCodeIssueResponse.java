package com.njwenglish.dto.member;

import com.njwenglish.entity.SignupCode;
import com.njwenglish.entity.enums.UserRole;
import java.time.OffsetDateTime;

public record SignupCodeIssueResponse(
    UserRole target, String code, String phone, OffsetDateTime expiresAt
) {
    public static SignupCodeIssueResponse from(SignupCode signupCode) {
        return new SignupCodeIssueResponse(signupCode.getTargetRole(), signupCode.getCode(),
            signupCode.getPhone(), signupCode.getExpiresAt());
    }
}
