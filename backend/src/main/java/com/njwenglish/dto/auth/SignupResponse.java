package com.njwenglish.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;

/** classRoomName은 반 코드 가입에만 있다. 개인 코드 응답에서는 필드가 아예 빠진다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignupResponse(
    UserRole role,
    String loginId,
    String studentName,
    String classRoomName,
    String initialPassword
) {

    public static SignupResponse of(UserRole role, String loginId, String studentName,
                                    String classRoomName) {
        return new SignupResponse(role, loginId, studentName, classRoomName,
            User.INITIAL_PASSWORD);
    }
}
