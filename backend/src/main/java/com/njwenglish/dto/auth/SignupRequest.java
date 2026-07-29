package com.njwenglish.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 세 가지 가입이 이 요청 하나를 쓴다. 서버가 code를 보고 종류를 판별하므로
 * 사용자는 "학생/학부모"를 고르지 않는다.
 *
 * <p>loginId와 비밀번호를 받지 않는다. phone이 로그인 아이디가 되고 비밀번호는 0000이다.
 *
 * @param code       반 코드(class_rooms.join_code) 또는 개인 코드(signup_codes.code)
 * @param name       반 코드 가입과 학부모 가입에 필수. 학생 개인 코드는 선생님이 등록한 이름을 쓴다
 * @param phone      본인 번호. 로그인 아이디가 된다
 * @param parentPhone 반 코드 가입에만 쓴다. 이 번호로 학부모용 개인 코드가 발급된다
 */
public record SignupRequest(
    @NotBlank String code,
    String name,
    @NotBlank String phone,
    String parentPhone
) {
}
