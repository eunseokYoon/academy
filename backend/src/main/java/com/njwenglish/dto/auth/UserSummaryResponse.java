package com.njwenglish.dto.auth;

import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserRole;

public record UserSummaryResponse(Long id, String name, UserRole role, boolean mustChangePassword) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getName(), user.getRole(),
            user.isMustChangePassword());
    }
}
