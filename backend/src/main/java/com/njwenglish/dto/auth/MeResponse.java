package com.njwenglish.dto.auth;

import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;

/**
 * phone은 <b>본인 번호라 마스킹하지 않는다.</b> 자기 번호를 자기가 보는 것이다.
 * /parent/me도 같은 값을 원본으로 내려준다.
 */
public record MeResponse(
    Long id, String name, UserRole role, String phone, boolean mustChangePassword
) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getName(), user.getRole(),
            user.getPhone(), user.isMustChangePassword());
    }
}
