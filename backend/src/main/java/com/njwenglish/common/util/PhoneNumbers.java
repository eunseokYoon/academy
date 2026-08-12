package com.njwenglish.common.util;

/**
 * 전화번호가 로그인 아이디라 정규화 지점이 한 곳이어야 한다.
 * 학부모는 010-1234-5678로 입력하고 DB에는 01012345678이 들어 있다.
 */
public final class PhoneNumbers {

    private static final int MASK_MIN_LENGTH = 7;

    private PhoneNumbers() {
    }

    /** 숫자가 아닌 문자를 모두 제거한다. login_id로 저장·조회하는 값은 언제나 이 결과다. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    /** 화면 표시용. 010-****-5678 */
    public static String mask(String phone) {
        String digits = normalize(phone);
        if (digits.length() < MASK_MIN_LENGTH) {
            return "****";
        }
        return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
    }
}
