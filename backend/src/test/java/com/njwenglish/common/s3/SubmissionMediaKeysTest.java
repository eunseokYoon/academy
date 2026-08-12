package com.njwenglish.common.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 발급한 키인지 대조하지 못하면 3단계 업로드에서 클라이언트가 임의 경로를 등록할 수 있다.
 */
class SubmissionMediaKeysTest {

    private final SubmissionMediaKeys keys =
        new SubmissionMediaKeys("test-secret-value-for-hmac-signing-0123456789");
    private final LocalDate today = LocalDate.of(2026, 5, 21);

    @Test
    @DisplayName("발급한 키는 같은 제출물에서 통과한다")
    void 발급한_키는_통과한다() {
        String photo = keys.issuePhoto(4412L, "image/webp", today);
        String video = keys.issueVideo(4412L, "video/mp4", today);

        assertThat(photo).startsWith("submissions/2026/05/").endsWith(".webp");
        assertThat(video).startsWith("submissions/2026/05/").endsWith(".mp4");
        assertThat(keys.matchesPhoto(photo, 4412L)).isTrue();
        assertThat(keys.matchesVideo(video, 4412L)).isTrue();
    }

    @Test
    @DisplayName("다른 제출물로 발급된 키는 거부된다")
    void 다른_제출물의_키는_거부된다() {
        String photo = keys.issuePhoto(4412L, "image/webp", today);
        String video = keys.issueVideo(4412L, "video/mp4", today);

        // 서명이 submissionId에 묶여 있어서 남의 제출물 키를 가져와도 걸린다
        assertThat(keys.matchesPhoto(photo, 4413L)).isFalse();
        assertThat(keys.matchesVideo(video, 4413L)).isFalse();
    }

    @Test
    @DisplayName("사진 키를 영상으로, 영상 키를 사진으로 등록할 수 없다")
    void 사진_키와_영상_키는_서로_바꿔_쓸_수_없다() {
        String photo = keys.issuePhoto(4412L, "image/webp", today);
        String video = keys.issueVideo(4412L, "video/mp4", today);

        assertThat(keys.matchesVideo(photo, 4412L)).isFalse();
        assertThat(keys.matchesPhoto(video, 4412L)).isFalse();
    }

    @Test
    @DisplayName("서명 없는 임의 경로는 거부된다")
    void 임의_경로는_거부된다() {
        assertThat(keys.matchesPhoto("submissions/2026/05/whatever.webp", 4412L)).isFalse();
        assertThat(keys.matchesPhoto("../../etc/passwd", 4412L)).isFalse();
        assertThat(keys.matchesPhoto("materials/2026/05/secret.pdf", 4412L)).isFalse();
        assertThat(keys.matchesPhoto(null, 4412L)).isFalse();
        assertThat(keys.matchesVideo("submissions/2026/05/whatever.mp4", 4412L)).isFalse();
        assertThat(keys.matchesVideo(null, 4412L)).isFalse();
    }

    @Test
    @DisplayName("서명을 한 글자라도 바꾸면 거부된다")
    void 서명이_변조되면_거부된다() {
        String s3Key = keys.issuePhoto(4412L, "image/webp", today);
        String tampered = s3Key.replace(".webp", "x.webp");

        assertThat(keys.matchesPhoto(tampered, 4412L)).isFalse();
    }

    @Test
    @DisplayName("사진은 이미지 형식만, 영상은 영상 형식만 허용한다")
    void 허용_형식이_구분된다() {
        assertThat(keys.isSupportedPhotoType("image/jpeg")).isTrue();
        assertThat(keys.isSupportedPhotoType("image/png")).isTrue();
        assertThat(keys.isSupportedPhotoType("image/webp")).isTrue();
        assertThat(keys.isSupportedPhotoType("video/mp4")).isFalse();
        assertThat(keys.isSupportedPhotoType("application/pdf")).isFalse();
        assertThat(keys.isSupportedPhotoType(null)).isFalse();

        assertThat(keys.isSupportedVideoType("video/mp4")).isTrue();
        // 아이폰에서 고른 영상은 대부분 quicktime이다. 빼면 아이폰 제출이 막힌다
        assertThat(keys.isSupportedVideoType("video/quicktime")).isTrue();
        assertThat(keys.isSupportedVideoType("video/webm")).isTrue();
        assertThat(keys.isSupportedVideoType("image/webp")).isFalse();
        assertThat(keys.isSupportedVideoType(null)).isFalse();
    }
}
