package com.njwenglish.dto.member;

import com.njwenglish.entity.enums.UserRole;

/**
 * target 생략 시 STUDENT. newPassword를 생략하면 서버가 임시 비밀번호를 만든다.
 *
 * <p>여기서는 길이 제한을 두지 않는다. 선생님이 0000처럼 짧은 값을 넣어도
 * must_change_password가 true라 다음 로그인에서 반드시 바뀐다.
 */
public record PasswordResetRequest(UserRole target, String newPassword) {
}
