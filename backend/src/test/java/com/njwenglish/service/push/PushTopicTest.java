package com.njwenglish.service.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PushTopicTest {

    @Test
    @DisplayName("묶는 종류는 넷이고 ck_push_daily_kind 와 같다")
    void 묶는_종류는_넷() {
        assertThat(Arrays.stream(PushTopic.values()).map(PushTopic::dailyKind)
            .filter(Objects::nonNull))
            .containsExactlyInAnyOrder("HOMEWORK_GRADED", "WEEKLY_SCORE",
                "ATTENDANCE_LESSON", "ATTENDANCE_CLINIC");
    }

    @Test
    @DisplayName("수업과 클리닉 출석은 kind 가 다르다 — 같은 날 둘 다 있는 게 정상이다")
    void 출석_kind는_둘() {
        assertThat(PushTopic.ATTENDANCE_LESSON.dailyKind())
            .isNotEqualTo(PushTopic.ATTENDANCE_CLINIC.dailyKind());
    }

    @Test
    @DisplayName("모든 종류에 받는 쪽이 적어도 하나 있다")
    void 받는_쪽이_있다() {
        for (PushTopic topic : PushTopic.values()) {
            assertThat(topic.studentScreen() != null || topic.parentScreen() != null)
                .as(topic.name()).isTrue();
        }
    }
}
