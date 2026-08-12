package com.njwenglish.common.security;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return user;
    }
}
