package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteCodesTest {

    @Test
    @DisplayName("혼동되는 0 O 1 I L이 코드에 들어가지 않는다")
    void 혼동_문자를_쓰지_않는다() {
        String codes = IntStream.range(0, 500)
            .mapToObj(i -> InviteCodes.generate())
            .reduce("", String::concat);

        assertThat(codes).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    @DisplayName("코드는 6자리 영문 대문자·숫자다")
    void 여섯자리다() {
        assertThat(InviteCodes.generate()).hasSize(6).matches("[A-Z2-9]{6}");
    }

    @Test
    @DisplayName("임시 비밀번호는 8자리이고 같은 문자셋을 쓴다")
    void 임시_비밀번호는_여덟자리다() {
        String password = InviteCodes.generate(InviteCodes.TEMPORARY_PASSWORD_LENGTH);

        assertThat(password).hasSize(8).matches("[A-Z2-9]{8}");
    }

    @Test
    @DisplayName("소문자·공백으로 들어와도 같은 값으로 정규화된다")
    void 대문자로_정규화한다() {
        assertThat(InviteCodes.normalize(" hk7f2q ")).isEqualTo("HK7F2Q");
        assertThat(InviteCodes.normalize(null)).isEmpty();
    }
}
