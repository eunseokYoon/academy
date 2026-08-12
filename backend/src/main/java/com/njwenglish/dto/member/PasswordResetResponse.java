package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.UserRole;

/** temporaryPassword는 이 응답에서 한 번만 내려간다. 저장하지도, 다시 조회하지도 못한다. */
public record PasswordResetResponse(UserRole target, String loginId, String temporaryPassword) {
}
