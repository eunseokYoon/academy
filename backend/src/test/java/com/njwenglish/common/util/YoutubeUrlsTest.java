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
        assertThat(YoutubeUrls.embedUrlOf("https://youtu.be/dQw4w9WgXcQ"))
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
        assertThat(YoutubeUrls.embedUrlOf(null)).isNull();
    }

    @Test
    @DisplayName("재생목록 링크에서 listId를 뽑는다")
    void parsesPlaylistId() {
        assertThat(YoutubeUrls.playlistId("https://www.youtube.com/playlist?list=PLabc123DEF"))
            .isEqualTo("PLabc123DEF");
    }

    @Test
    @DisplayName("영상과 재생목록이 같이 있으면 둘 다 살린다 — 영상으로 열고 목록으로 이어본다")
    void videoAndPlaylistBothKept() {
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123DEF";
        assertThat(YoutubeUrls.embedUrlOf(url))
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ?list=PLabc123DEF");
    }

    @Test
    @DisplayName("단일 영상 링크는 그대로 embed 주소가 된다")
    void videoStillWorks() {
        assertThat(YoutubeUrls.embedUrlOf("https://youtu.be/dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("재생목록에는 videoId가 없다 — 썸네일을 그리지 않는 근거다")
    void playlistHasNoVideoId() {
        assertThat(YoutubeUrls.videoId("https://www.youtube.com/playlist?list=PLabc123DEF"))
            .isNull();
    }

    @Test
    @DisplayName("YouTube가 아니면 null이다")
    void rejectsOtherHosts() {
        assertThat(YoutubeUrls.embedUrlOf("https://vimeo.com/12345")).isNull();
        assertThat(YoutubeUrls.embedUrlOf(null)).isNull();
        assertThat(YoutubeUrls.embedUrlOf("  ")).isNull();
    }

    @Test
    @DisplayName("youtu.be 단축 링크의 list도 재생목록으로 읽는다 — 모바일 공유 시트가 주는 형식이다")
    void shortLinkWithPlaylistIsPlaylist() {
        String url = "https://youtu.be/dQw4w9WgXcQ?list=PLabc123DEF&index=2";
        assertThat(YoutubeUrls.playlistId(url)).isEqualTo("PLabc123DEF");
        assertThat(YoutubeUrls.embedUrlOf(url))
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ?list=PLabc123DEF");
    }

    @Test
    @DisplayName("list가 없는 youtu.be 링크는 그대로 단일 영상이다")
    void shortLinkWithoutPlaylistStaysVideo() {
        assertThat(YoutubeUrls.playlistId("https://youtu.be/dQw4w9WgXcQ")).isNull();
        assertThat(YoutubeUrls.embedUrlOf("https://youtu.be/dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("영상과 재생목록이 둘 다 있으면 embed/{videoId}?list= 다 — 일부 공개 목록에서 되는 형식")
    void videoWithPlaylistKeepsBoth() {
        assertThat(YoutubeUrls.embedUrlOf(
            "https://www.youtube.com/watch?v=im83SqpKKJ0&list=PLSaF8K8pRF_s&index=1&t=3s"))
            .isEqualTo("https://www.youtube.com/embed/im83SqpKKJ0?list=PLSaF8K8pRF_s");
    }

    @Test
    @DisplayName("임베드 주소를 그대로 붙여도 재생목록이 안 떨어진다")
    void embedUrlWithPlaylistRoundTrips() {
        String url = "https://www.youtube.com/embed/im83SqpKKJ0?list=PLSaF8K8pRF_s";
        assertThat(YoutubeUrls.embedUrlOf(url)).isEqualTo(url);
    }

    @Test
    @DisplayName("youtu.be 단축 링크도 영상 ID를 살려서 재생목록을 붙인다")
    void shortLinkKeepsBoth() {
        assertThat(YoutubeUrls.embedUrlOf("https://youtu.be/im83SqpKKJ0?list=PLSaF8K8pRF_s"))
            .isEqualTo("https://www.youtube.com/embed/im83SqpKKJ0?list=PLSaF8K8pRF_s");
    }

    @Test
    @DisplayName("재생목록만 있으면 videoseries다 — 영상 ID가 없어 다른 형식을 만들 수 없다")
    void playlistOnlyStaysVideoseries() {
        assertThat(YoutubeUrls.embedUrlOf("https://www.youtube.com/playlist?list=PLSaF8K8pRF_s"))
            .isEqualTo("https://www.youtube.com/embed/videoseries?list=PLSaF8K8pRF_s");
    }
}
