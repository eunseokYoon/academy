package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthWeeksTest {

    @Test
    @DisplayName("평년 2월에는 5주차가 없고, 윤년 2월·31일 달에는 있다")
    void fifthWeekExistsOnlyWhenTheMonthReachesDay29() {
        assertThat(MonthWeeks.exists(2027, 2, 5)).isFalse();
        assertThat(MonthWeeks.exists(2028, 2, 5)).isTrue();
        assertThat(MonthWeeks.exists(2026, 10, 5)).isTrue();
        assertThat(MonthWeeks.exists(2027, 2, 4)).isTrue();
    }

    @Test
    @DisplayName("윤년 2월 5주차는 하루짜리다")
    void leapFebruaryFifthWeekIsOneDay() {
        assertThat(MonthWeeks.startOf(2028, 2, 5)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(MonthWeeks.endOf(2028, 2, 5)).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    @DisplayName("화면이 만들 수 없는 월·주차는 400이다")
    void outOfRangeIsBadRequest() {
        for (int[] bad : new int[][] {{2026, 13, 1}, {2026, 0, 1}, {2026, 9, 0}, {2026, 9, 6}}) {
            assertThatThrownBy(() -> MonthWeeks.exists(bad[0], bad[1], bad[2]))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        }
    }
}
