package com.njwenglish.common.s3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 게시판 사진의 s3Key. 발급한 키에 서명을 박아 두고 등록 시 대조한다.
 *
 * <p><b>서명이 묶이는 대상이 SubmissionMediaKeys와 다르다.</b> 숙제 사진은 submissionId에
 * 묶지만, 게시판 사진은 <b>글이 만들어지기 전에</b> 올라가므로 묶을 글이 없다. 대신 발급받은
 * 사용자(userId)에 묶는다 — 남이 발급받은 키나 버킷 내 임의 경로를 자기 글에 붙일 수 없다.
 *
 * <p><b>서명 대상은 {@code userId:uuid:확장자}이고 날짜 부분은 들어가지 않는다.</b>
 * 따라서 발급된 키의 날짜(qna/2026/08/)를 바꿔도 대조는 통과한다. 문제가 안 되는 이유는
 * presigned URL이 키 문자열 그대로에 묶여 있어 바뀐 경로에는 S3 객체가 없고, 서명이 userId에
 * 묶여 있어 남이 발급받은 키를 자기 글에 붙일 수 없기 때문이다. {@link SubmissionMediaKeys}도
 * 같은 설계다 — 규칙을 갈라 놓지 마라.
 *
 * <p>프리픽스가 qna/로 분리되어 있다. submissions/에는 12개월 수명주기가 걸리지만
 * 게시판 사진의 보관 기간은 <b>아직 정해지지 않았다</b>. 규칙을 임의로 걸지 마라.
 *
 * <p><b>영상 확장자를 추가하지 마라.</b> 숙제 영상에 1개·100MB 상한을 걸어 둔 이유가
 * 저장 비용인데, 게시판이 그 상한을 우회하는 통로가 된다.
 */
@Component
public class QnaMediaKeys {

    private static final String PREFIX = "qna";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_LENGTH = 16;

    private static final Map<String, String> PHOTO_EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp");

    private static final Pattern KEY_PATTERN = Pattern.compile(
        "^qna/(\\d{4})/(\\d{2})/([0-9a-f-]{36})-([0-9a-f]{" + SIGNATURE_LENGTH + "})"
            + "\\.(jpg|png|webp)$");

    private final SecretKeySpec signingKey;

    /** 서명 키는 JWT 시크릿을 그대로 쓴다. SubmissionMediaKeys와 같은 이유다. */
    public QnaMediaKeys(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM);
    }

    public boolean isSupportedPhotoType(String contentType) {
        return contentType != null && PHOTO_EXTENSIONS.containsKey(contentType);
    }

    /** qna/2026/08/{uuid}-{서명}.webp */
    public String issuePhoto(Long userId, String contentType, LocalDate today) {
        String extension = PHOTO_EXTENSIONS.get(contentType);
        String uuid = UUID.randomUUID().toString();
        return "%s/%d/%02d/%s-%s.%s".formatted(
            PREFIX, today.getYear(), today.getMonthValue(), uuid,
            sign(userId, uuid, extension), extension);
    }

    /** 형식과 서명이 모두 맞아야 통과한다. 서명은 시간 상수 비교로 대조한다. */
    public boolean matchesPhoto(String s3Key, Long userId) {
        if (s3Key == null) {
            return false;
        }
        Matcher matcher = KEY_PATTERN.matcher(s3Key);
        if (!matcher.matches()) {
            return false;
        }
        String expected = sign(userId, matcher.group(3), matcher.group(5));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            matcher.group(4).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(Long userId, String uuid, String extension) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(
                (userId + ":" + uuid + ":" + extension).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, SIGNATURE_LENGTH);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("s3Key 서명에 실패했습니다.", e);
        }
    }
}
