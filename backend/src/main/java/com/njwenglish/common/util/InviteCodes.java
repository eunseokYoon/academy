package com.njwenglish.common.util;

import java.security.SecureRandom;

/**
 * 가입 코드 문자열 생성기. 유일성 검사는 호출하는 서비스가 한다
 * (signup_codes.code와 class_rooms.join_code 두 곳을 모두 봐야 하기 때문).
 */
public final class InviteCodes {

    /** 구두로 전달하는 값이라 헷갈리는 0·O·1·I를 뺐다. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /** 입력값 정규화. 학부모가 소문자로 적어 오는 일이 흔하다. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
