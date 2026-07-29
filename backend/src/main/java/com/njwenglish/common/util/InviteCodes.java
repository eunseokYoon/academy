package com.njwenglish.common.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 가입 코드·임시 비밀번호 문자열 생성기. 유일성 검사는 InviteCodeIssuer가 한다
 * (signup_codes.code와 class_rooms.join_code 두 곳을 모두 봐야 하기 때문).
 */
public final class InviteCodes {

    /** 선생님이 구두로 불러 주고 상대가 받아 적는 값이라 헷갈리는 0·O·1·I·L을 뺐다. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 6;

    /** 임시 비밀번호. 코드보다 길게 잡되 같은 문자셋을 쓴다. 역시 구두로 전달한다. */
    public static final int TEMPORARY_PASSWORD_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {
    }

    public static String generate() {
        return generate(CODE_LENGTH);
    }

    public static String generate(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /** 입력값 정규화. 학부모가 소문자로 적어 오는 일이 흔하다. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
