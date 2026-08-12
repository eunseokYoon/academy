package com.njwenglish.dto.member;

import com.njwenglish.entity.SignupCode;
import java.time.OffsetDateTime;

/** 선생님이 구두로 전달하는 값이라 화면에 복사 버튼과 함께 번호를 같이 띄운다. */
public record SignupCodeResponse(String code, String phone, OffsetDateTime expiresAt) {

    public static SignupCodeResponse from(SignupCode signupCode) {
        return new SignupCodeResponse(
            signupCode.getCode(), signupCode.getPhone(), signupCode.getExpiresAt());
    }
}
