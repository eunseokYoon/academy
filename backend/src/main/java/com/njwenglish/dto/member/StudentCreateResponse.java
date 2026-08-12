package com.njwenglish.dto.member;

/**
 * 등록과 동시에 학생 코드를 돌려준다. 별도 화면에 다시 들어가게 만들면 실제로 안 쓴다.
 *
 * <p><b>학부모 코드는 없다.</b> 학부모 계정은 등록 시점에 보호자 번호로 바로 만들어지고
 * 초기 비밀번호는 0000이다. 선생님이 전달할 것은 학생 코드 한 장뿐이다.
 */
public record StudentCreateResponse(Long studentId, SignupCodeResponse signupCode) {
}
