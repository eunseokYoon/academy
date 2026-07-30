package com.njwenglish.common.s3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 발급한 s3Key에 서명을 박아 두고 등록 시 대조한다.
 *
 * <p>업로드는 3단계(URL 발급 → S3로 직접 PUT → 서버에 등록)라, 3단계에서 클라이언트가 보낸
 * s3Key를 그대로 믿으면 남의 제출물 파일이나 버킷 내 임의 경로를 자기 제출물에 붙일 수 있다.
 * 서명이 submissionId에 묶여 있어서, 다른 제출물로 발급된 키를 가져와도 대조에서 걸린다.
 *
 * <p>발급 키를 테이블에 임시 저장하는 방법도 있지만, 서명이면 저장도 만료 정리도 필요 없다.
 *
 * <p>사진과 영상이 같은 <b>submissions/ 프리픽스</b>를 쓴다. Phase 8에서 보관 기간이 정해져
 * 수명 주기 규칙을 걸 때 둘이 함께 들어가야 하기 때문이다. 대신 확장자가 서명에 포함되어 있어
 * 사진용으로 발급한 키를 영상으로 등록할 수는 없다.
 */
@Component
public class SubmissionMediaKeys {

    private static final String PREFIX = "submissions";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_LENGTH = 16;

    /** 사진. PDF·문서는 허용 대상이 아니다. */
    private static final Map<String, String> PHOTO_EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp");

    /**
     * 영상. quicktime(.mov)을 빼면 아이폰에서 고른 영상이 대부분 거부된다.
     * 다만 아이폰 기본 녹화가 HEVC라 일부 브라우저에서 재생이 안 될 수 있다 —
     * 서버 트랜스코딩은 범위 밖이라 여기서 해결할 수 있는 문제가 아니다.
     */
    private static final Map<String, String> VIDEO_EXTENSIONS = Map.of(
        "video/mp4", "mp4",
        "video/quicktime", "mov",
        "video/webm", "webm");

    private static final Set<String> PHOTO_SUFFIXES = Set.of("jpg", "png", "webp");
    private static final Set<String> VIDEO_SUFFIXES = Set.of("mp4", "mov", "webm");

    private static final Pattern KEY_PATTERN = Pattern.compile(
        "^submissions/(\\d{4})/(\\d{2})/([0-9a-f-]{36})-([0-9a-f]{" + SIGNATURE_LENGTH + "})"
            + "\\.(jpg|png|webp|mp4|mov|webm)$");

    private final SecretKeySpec signingKey;

    /**
     * 서명 키는 JWT 시크릿을 그대로 쓴다. 둘 다 "서버만 아는 값"이라는 요구가 같고,
     * 환경변수를 하나 더 늘리면 배포에서 빠뜨렸을 때 조용히 검증이 무의미해진다.
     */
    public SubmissionMediaKeys(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM);
    }

    public boolean isSupportedPhotoType(String contentType) {
        return contentType != null && PHOTO_EXTENSIONS.containsKey(contentType);
    }

    public boolean isSupportedVideoType(String contentType) {
        return contentType != null && VIDEO_EXTENSIONS.containsKey(contentType);
    }

    /** submissions/2026/05/{uuid}-{서명}.webp */
    public String issuePhoto(Long submissionId, String contentType, LocalDate today) {
        return issue(submissionId, PHOTO_EXTENSIONS.get(contentType), today);
    }

    /** submissions/2026/05/{uuid}-{서명}.mp4 */
    public String issueVideo(Long submissionId, String contentType, LocalDate today) {
        return issue(submissionId, VIDEO_EXTENSIONS.get(contentType), today);
    }

    public boolean matchesPhoto(String s3Key, Long submissionId) {
        return matches(s3Key, submissionId, PHOTO_SUFFIXES);
    }

    public boolean matchesVideo(String s3Key, Long submissionId) {
        return matches(s3Key, submissionId, VIDEO_SUFFIXES);
    }

    private String issue(Long submissionId, String extension, LocalDate today) {
        String uuid = UUID.randomUUID().toString();
        return "%s/%d/%02d/%s-%s.%s".formatted(
            PREFIX, today.getYear(), today.getMonthValue(), uuid,
            sign(submissionId, uuid, extension), extension);
    }

    /** 형식·용도·서명이 모두 맞아야 통과한다. 서명은 시간 상수 비교로 대조한다. */
    private boolean matches(String s3Key, Long submissionId, Set<String> allowedSuffixes) {
        if (s3Key == null) {
            return false;
        }
        Matcher matcher = KEY_PATTERN.matcher(s3Key);
        if (!matcher.matches() || !allowedSuffixes.contains(matcher.group(5))) {
            return false;
        }
        String expected = sign(submissionId, matcher.group(3), matcher.group(5));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            matcher.group(4).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(Long submissionId, String uuid, String extension) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(
                (submissionId + ":" + uuid + ":" + extension).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, SIGNATURE_LENGTH);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("s3Key 서명에 실패했습니다.", e);
        }
    }
}
