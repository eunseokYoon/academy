package com.njwenglish.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 도착 시각 슬롯 계산. <b>"마지막 슬롯은 종료 1시간 전"이 이 스위트의 전부다.</b>
 * 규칙이 여기와 화면 두 곳으로 갈라지면 학생이 고른 시각을 서버가 거절하기 시작한다.
 */
class ClinicSlotTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    private Clinic clinic(LocalTime start, LocalTime end) {
        return Clinic.open(Fixtures.teacherEntity(1L), DATE, start, end, null, null);
    }

    @Test
    @DisplayName("17:00~22:00이면 17·18·19·20·21시 다섯 개다 — 22시는 없다")
    void fiveHourClinicHasFiveSlots() {
        assertThat(clinic(LocalTime.of(17, 0), LocalTime.of(22, 0)).slots())
            .containsExactly(LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0),
                LocalTime.of(20, 0), LocalTime.of(21, 0));
    }

    @Test
    @DisplayName("끝이 30분 단위면 남는 30분에는 슬롯을 만들지 않는다")
    void halfHourTailProducesNoSlot() {
        // 20:30에 오면 한 시간을 못 채운다. 20:00이 마지막이다
        assertThat(clinic(LocalTime.of(17, 0), LocalTime.of(21, 30)).slots())
            .containsExactly(LocalTime.of(17, 0), LocalTime.of(18, 0), LocalTime.of(19, 0),
                LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("슬롯은 정시가 아니라 클리닉 시작 시각에 붙는다")
    void slotsAnchorToStartTimeNotClockHour() {
        // 정시로 맞추면 17:30~18:00이 아무도 못 오는 구간이 된다
        assertThat(clinic(LocalTime.of(17, 30), LocalTime.of(21, 30)).slots())
            .containsExactly(LocalTime.of(17, 30), LocalTime.of(18, 30), LocalTime.of(19, 30),
                LocalTime.of(20, 30));
    }

    @Test
    @DisplayName("한 시간짜리 클리닉은 시작 시각 하나뿐이다")
    void oneHourClinicHasSingleSlot() {
        assertThat(clinic(LocalTime.of(17, 0), LocalTime.of(18, 0)).slots())
            .containsExactly(LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("한 시간이 안 되는 클리닉에는 고를 슬롯이 없다")
    void shorterThanAnHourHasNoSlot() {
        assertThat(clinic(LocalTime.of(17, 0), LocalTime.of(17, 30)).slots()).isEmpty();
    }

    @Test
    @DisplayName("자정을 넘겨도 슬롯 계산이 되감기지 않는다")
    void lateNightClinicDoesNotWrapAround() {
        // LocalTime.plusHours는 24시를 넘기면 00시로 되감긴다. 무한 루프가 되면 안 된다
        assertThat(clinic(LocalTime.of(22, 0), LocalTime.of(23, 59)).slots())
            .containsExactly(LocalTime.of(22, 0));
    }

    @Test
    @DisplayName("hasSlot은 목록 밖의 시각을 거절한다")
    void hasSlotRejectsArbitraryTimes() {
        Clinic clinic = clinic(LocalTime.of(17, 0), LocalTime.of(22, 0));

        assertThat(clinic.hasSlot(LocalTime.of(21, 0))).isTrue();
        // 마지막 슬롯 다음 시각. 화면에는 안 뜨지만 API로는 그대로 올라온다
        assertThat(clinic.hasSlot(LocalTime.of(22, 0))).isFalse();
        assertThat(clinic.hasSlot(LocalTime.of(19, 37))).isFalse();
        assertThat(clinic.hasSlot(LocalTime.of(16, 0))).isFalse();
        assertThat(clinic.hasSlot(null)).isFalse();
    }
}
