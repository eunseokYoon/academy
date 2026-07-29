package com.njwenglish.dto.member;

/**
 * 보낸 필드만 바꾼다. null은 "그대로 두기"라서 memo를 비우려면 빈 문자열을 보낸다.
 *
 * <p>전화번호는 대상이 가입했는지에 따라 바뀌는 곳이 다르다.
 * 미가입이면 발급된 코드의 대조 번호를, 가입했으면 로그인 아이디(users.login_id)를 고친다.
 * 단 이미 가입한 학부모의 번호는 여기서 못 바꾼다 — 형제·자매가 공유하는 계정이라
 * 본인이 P-5에서 바꾼다.
 */
public record StudentUpdateRequest(
    String name,
    String memo,
    String studentPhone,
    String parentPhone
) {
}
