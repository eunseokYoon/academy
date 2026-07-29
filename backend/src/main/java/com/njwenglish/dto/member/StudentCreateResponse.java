package com.njwenglish.dto.member;

/**
 * 등록과 동시에 코드 두 장을 돌려준다. 별도 화면에 다시 들어가게 만들면 실제로 안 쓴다.
 * 학생용과 학부모용이 섞이면 서로의 계정으로 가입하므로 화면에서 번호와 함께 크게 보여줄 것.
 */
public record StudentCreateResponse(Long studentId, SignupCodes signupCodes) {

    public record SignupCodes(SignupCodeResponse student, SignupCodeResponse parent) {
    }
}
