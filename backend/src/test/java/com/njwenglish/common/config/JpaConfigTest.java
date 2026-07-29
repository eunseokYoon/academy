package com.njwenglish.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * created_at·updated_at이 전부 OffsetDateTime(TIMESTAMPTZ)이다. 감사 시각 공급자가
 * 기본값(LocalDateTime)이면 모든 INSERT가 변환 실패로 500이 된다.
 */
class JpaConfigTest {

    @Test
    @DisplayName("감사 시각은 OffsetDateTime으로 공급된다")
    void 감사_시각은_OffsetDateTime이다() {
        assertThat(new JpaConfig().auditingDateTimeProvider().getNow())
            .get().isInstanceOf(OffsetDateTime.class);
    }
}
