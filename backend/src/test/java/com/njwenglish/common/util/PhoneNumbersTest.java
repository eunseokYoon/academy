package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    @DisplayName("하이픈과 공백을 제거해 숫자만 남긴다")
    void 하이픈과_공백을_제거한다() {
        assertThat(PhoneNumbers.normalize(" 010-1234-5678 ")).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("이미 정규화된 번호는 그대로 반환한다")
    void 정규화된_번호는_그대로다() {
        assertThat(PhoneNumbers.normalize("01012345678")).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("null은 빈 문자열로 정규화한다")
    void null은_빈_문자열이다() {
        assertThat(PhoneNumbers.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("가운데 네 자리를 가린다")
    void 가운데_네_자리를_가린다() {
        assertThat(PhoneNumbers.mask("01012345678")).isEqualTo("010-****-5678");
    }

    @Test
    @DisplayName("자리수가 모자란 값은 전부 가린다")
    void 짧은_번호는_전부_가린다() {
        assertThat(PhoneNumbers.mask("1234")).isEqualTo("****");
    }
}
