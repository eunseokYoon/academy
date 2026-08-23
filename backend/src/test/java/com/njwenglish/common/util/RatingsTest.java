package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 별점은 API에서 0.5~5.0이고 DB에서 1~10이다. 이 변환이 두 곳에 생기면 갈라진다.
 */
class RatingsTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1.0, 2.5, 4.5, 5.0})
    @DisplayName("0.5 단위 0.5~5.0은 유효하다")
    void 반개_단위는_유효하다(double rating) {
        assertThat(Ratings.isValid(rating)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {4.3, 1.1, 0.25, 2.75})
    @DisplayName("0.5 단위가 아니면 무효다")
    void 반개_단위가_아니면_무효다(double rating) {
        assertThat(Ratings.isValid(rating)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, 5.5, 10.0})
    @DisplayName("범위를 벗어나면 무효다")
    void 범위_밖은_무효다(double rating) {
        // 0은 "별점 안 줌"이 아니라 무효다. 별점은 필수다
        assertThat(Ratings.isValid(rating)).isFalse();
    }

    @Test
    @DisplayName("4.5는 9로 저장되고 9는 4.5로 돌아온다")
    void 왕복_변환이_맞는다() {
        assertThat(Ratings.toStored(4.5)).isEqualTo((short) 9);
        assertThat(Ratings.toDisplay((short) 9)).isEqualTo(4.5);
    }

    @Test
    @DisplayName("평균은 소수 첫째 자리까지다")
    void 평균은_소수_첫째_자리다() {
        // 저장값 평균 8.6667 → 4.33... → 4.3
        assertThat(Ratings.averageToDisplay(8.6667)).isEqualTo(4.3);
    }

    @Test
    @DisplayName("후기가 없으면 평균은 null이다")
    void 후기가_없으면_평균은_null이다() {
        // 0.0을 내려주면 화면이 "별점 0점"으로 읽는다
        assertThat(Ratings.averageToDisplay(null)).isNull();
    }
}
