package com.njwenglish.dto.classroom;

/**
 * regenerate만 true면 코드를 새로 뽑고, active만 보내면 코드는 그대로 두고 여닫는다.
 * 둘 다 생략이면 아무것도 바뀌지 않는다.
 *
 * <p>재발급하면 이전 코드는 즉시 무효다. 이미 가입한 학생의 enrollments는 그대로다.
 */
public record JoinCodeRequest(Boolean regenerate, Boolean active) {
}
