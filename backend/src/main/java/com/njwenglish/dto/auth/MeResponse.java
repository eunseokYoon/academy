package com.njwenglish.dto.auth;

import com.njwenglish.common.util.PhoneNumbers;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;

/** phone은 서버에서 마스킹해 내려간다 (010-****-1234). */
public record MeResponse(
    Long id, String name, UserRole role, String phone, boolean mustChangePassword
) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getName(), user.getRole(),
            PhoneNumbers.mask(user.getPhone()), user.isMustChangePassword());
    }
}
