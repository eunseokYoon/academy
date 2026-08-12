package com.njwenglish.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YoutubeUrlsTest {

    @ParameterizedTest
    @DisplayName("선생님이 주소창에서 복사해 오는 세 형식을 모두 파싱한다")
    @ValueSource(strings = {
        "https://youtu.be/dQw4w9WgXcQ",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://www.youtube.com/embed/dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ&t=30s",
        "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://youtu.be/dQw4w9WgXcQ?si=abcdef",
    })
    void 세_형식을_파싱한다(String url) {
        assertThat(YoutubeUrls.videoId(url)).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("파싱한 id로 iframe에 넣을 embed 주소를 만든다")
    void embed_주소를_만든다() {
        assertThat(YoutubeUrls.embedUrl(YoutubeUrls.videoId("https://youtu.be/dQw4w9WgXcQ")))
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @ParameterizedTest
    @DisplayName("YouTube가 아니거나 형식이 어긋나면 null이다")
    @ValueSource(strings = {
        "https://vimeo.com/12345678",
        "https://youtu.be/tooshort",
        "그냥 문자열",
    })
    void 잘못된_주소는_null이다(String url) {
        assertThat(YoutubeUrls.videoId(url)).isNull();
    }

    @Test
    @DisplayName("빈 값은 null이다")
    void 빈_값은_null이다() {
        assertThat(YoutubeUrls.videoId(null)).isNull();
        assertThat(YoutubeUrls.videoId("  ")).isNull();
        assertThat(YoutubeUrls.embedUrl(null)).isNull();
    }
}
