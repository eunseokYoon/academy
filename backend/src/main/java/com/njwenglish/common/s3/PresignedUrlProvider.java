package com.njwenglish.common.s3;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 버킷은 비공개다. 숙제 사진에는 학생 필기와 이름이 담겨 있어서
 * 공개 URL을 만들면 링크가 도는 순간 누구나 본다. 읽기도 매번 presign한다.
 */
@Component
@Slf4j
public class PresignedUrlProvider {

    private final S3Presigner presigner;
    private final S3Client s3Client;
    private final String bucket;
    private final Duration uploadExpiry;
    private final Duration readExpiry;

    public PresignedUrlProvider(S3Presigner presigner,
                                S3Client s3Client,
                                @Value("${app.s3.bucket}") String bucket,
                                @Value("${app.s3.presign-expiry-seconds}") long uploadExpirySeconds,
                                @Value("${app.s3.read-expiry-seconds}") long readExpirySeconds) {
        this.presigner = presigner;
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.uploadExpiry = Duration.ofSeconds(uploadExpirySeconds);
        this.readExpiry = Duration.ofSeconds(readExpirySeconds);
    }

    /**
     * 업로드용 PUT URL. 클라이언트는 여기에 발급 때와 <b>같은 Content-Type, 같은 크기</b>로
     * PUT해야 한다. 둘 다 서명에 들어가서 다르면 S3가 403으로 거절한다.
     *
     * <p><b>크기(Content-Length)를 서명하는 이유</b>(2026-09-30 리뷰): 서버의 용량 상한은
     * 발급 요청의 bytes만 검사한다. 서명에 크기가 없으면 {@code bytes: 1}로 URL을 받아
     * 5GB를 올릴 수 있었다 — 숙제 영상 100MB 상한(CLAUDE.md 6번)이 저장 비용을 막는 유일한
     * 장치인데 실제로는 걸리지 않았다. 웹·앱 모두 발급에 보낸 값과 같은 크기로 PUT한다.
     */
    public String uploadUrl(String s3Key, String contentType, long bytes) {
        PutObjectRequest put = PutObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .contentType(contentType)
            .contentLength(bytes)
            .build();

        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(uploadExpiry)
                .putObjectRequest(put)
                .build())
            .url()
            .toString();
    }

    /** 조회용 GET URL. 응답에 담겨 나가므로 유효기간이 짧다. */
    public String readUrl(String s3Key) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .build();

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(readExpiry)
                .getObjectRequest(get)
                .build())
            .url()
            .toString();
    }

    /**
     * 다운로드용 GET URL. 브라우저가 <b>원본 파일명으로 저장</b>하도록
     * Content-Disposition을 함께 서명한다.
     *
     * <p>파일명이 한글이면 {@code filename="..."}만으로는 깨진다. RFC 5987의
     * {@code filename*=UTF-8''}을 같이 넣고, 앞의 filename은 ASCII 대체값으로 둔다.
     *
     * <p>attachment로 고정하는 이유: inline이면 브라우저가 파일을 열어 버리고,
     * 자료실은 미리보기 없이 다운로드만 하기로 확정되어 있다.
     */
    public String attachmentUrl(String s3Key, String fileName, Duration expiry) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .responseContentDisposition(contentDisposition(fileName))
            .build();

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(get)
                .build())
            .url()
            .toString();
    }

    /**
     * 사진 삭제 시 S3 객체도 함께 지운다. <b>실패해도 예외를 던지지 않는다.</b>
     * 트랜잭션을 되돌리면 학생이 지운 사진이 화면에 계속 뜬다.
     * 고아 객체 몇 개가 남는 편이 그것보다 낫다.
     */
    public void deleteQuietly(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build());
        } catch (RuntimeException e) {
            log.warn("S3 객체 삭제 실패. 고아 객체로 남는다: {}", s3Key, e);
        }
    }

    /**
     * 큰따옴표·역슬래시·개행은 헤더를 깨뜨리므로 ASCII 대체값에서 지운다.
     * 실제 이름은 filename*이 전달하므로 대체값이 거칠어도 된다.
     */
    private String contentDisposition(String fileName) {
        String fallback = fileName.replaceAll("[^\\x20-\\x7E]", "_").replaceAll("[\"\\\\]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"%s\"; filename*=UTF-8''%s".formatted(fallback, encoded);
    }
}
