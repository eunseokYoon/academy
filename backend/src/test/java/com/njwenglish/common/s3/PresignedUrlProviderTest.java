package com.njwenglish.common.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * presign은 네트워크 없이 계산되므로 진짜 S3Presigner로 서명 헤더를 본다.
 *
 * <p>2026-09-30 리뷰: 업로드 URL에 크기가 서명되지 않아 {@code bytes: 1}로 발급받고 5GB를
 * 올릴 수 있었다. 용량 상한이 발급 요청의 숫자에만 걸려 있었다.
 */
class PresignedUrlProviderTest {

    private final S3Presigner presigner = S3Presigner.builder()
        .region(Region.AP_NORTHEAST_2)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("AKIAEXAMPLE", "secret")))
        .build();

    private final PresignedUrlProvider provider = new PresignedUrlProvider(
        presigner, mock(S3Client.class), "academy-test", 300, 300);

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("업로드 URL은 Content-Type과 Content-Length를 함께 서명한다 — 다른 크기의 PUT은 S3가 거절한다")
    void uploadUrlSignsContentLength() {
        String url = provider.uploadUrl("submissions/2026/09/30/1_abc.mp4", "video/mp4",
            52_428_800L);

        String signedHeaders = URLDecoder.decode(
            url.replaceAll(".*[?&]X-Amz-SignedHeaders=([^&]*).*", "$1"), StandardCharsets.UTF_8);
        assertThat(signedHeaders.split(";")).contains("content-length", "content-type");
    }
}
