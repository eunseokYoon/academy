package com.njwenglish.common.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 게시판 사진은 글이 만들어지기 전에 올라간다. 그래서 서명이 글이 아니라
 * <b>작성자(userId)</b>에 묶인다. 남이 발급받은 키를 자기 글에 붙일 수 없어야 한다.
 */
class QnaMediaKeysTest {

    private static final String SECRET = "test-secret-value-for-hmac-signing-0123456789";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    private final QnaMediaKeys keys = new QnaMediaKeys(SECRET);

    @Test
    @DisplayName("발급한 키는 같은 userId로 대조하면 통과한다")
    void issueThenMatch() {
        String key = keys.issuePhoto(7L, "image/webp", TODAY);

        assertThat(key).startsWith("qna/2026/08/").endsWith(".webp");
        assertThat(keys.matchesPhoto(key, 7L)).isTrue();
    }

    @Test
    @DisplayName("남의 userId로 발급된 키는 대조에서 걸린다")
    void otherUserKeyRejected() {
        String key = keys.issuePhoto(7L, "image/webp", TODAY);

        assertThat(keys.matchesPhoto(key, 8L)).isFalse();
    }

    @Test
    @DisplayName("서명을 손댄 키는 걸린다")
    void tamperedKeyRejected() {
        String key = keys.issuePhoto(7L, "image/webp", TODAY);
        String tampered = key.substring(0, key.length() - 6) + "0.webp";

        assertThat(keys.matchesPhoto(tampered, 7L)).isFalse();
    }

    @Test
    @DisplayName("버킷 내 임의 경로는 형식에서 걸린다")
    void arbitraryPathRejected() {
        assertThat(keys.matchesPhoto("submissions/2026/08/other.webp", 7L)).isFalse();
        assertThat(keys.matchesPhoto(null, 7L)).isFalse();
    }

    @Test
    @DisplayName("허용 타입은 webp·jpeg·png뿐이다 — 영상은 받지 않는다")
    void onlyPhotoTypes() {
        assertThat(keys.isSupportedPhotoType("image/webp")).isTrue();
        assertThat(keys.isSupportedPhotoType("image/jpeg")).isTrue();
        assertThat(keys.isSupportedPhotoType("image/png")).isTrue();
        assertThat(keys.isSupportedPhotoType("video/mp4")).isFalse();
        assertThat(keys.isSupportedPhotoType("application/pdf")).isFalse();
        assertThat(keys.isSupportedPhotoType(null)).isFalse();
    }
}
