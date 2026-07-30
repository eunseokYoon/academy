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
 * 온라인 테스트 해설지(정답지) 파일의 s3Key를 발급·대조한다.
 * SubmissionMediaKeys와 같은 이유로 서명을 키에 박는다 — 3단계 업로드에서 클라이언트가 보낸
 * 경로를 그대로 믿으면 버킷 내 임의 객체를 해설지로 붙일 수 있다.
 *
 * <p>제출물과 달리 <b>발급 시점에 testId가 없다.</b> 출제 화면에서 파일을 먼저 올리고
 * 그 키로 테스트를 만들기 때문이다. 그래서 서명은 발급한 선생님에게 묶는다.
 * 강사가 1명이라 실질적인 제약은 "우리 서버가 발급한 키인가"가 된다.
 *
 * <p>문제지 파일은 여기 올리지 않는다. 시험은 종이로 보고 서버가 가진 건 정답 배열과 해설지뿐이다.
 */
@Component
public class OnlineTestAnswerKeys {

    private static final String PREFIX = "online-tests";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_LENGTH = 16;

    /** 해설지는 배포용 문서다. html·js처럼 서빙 시 XSS가 되는 형식은 받지 않는다. */
    private static final Map<String, String> EXTENSIONS = Map.of(
        "application/pdf", "pdf",
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp");

    private static final Pattern KEY_PATTERN = Pattern.compile(
        "^online-tests/(\\d{4})/(\\d{2})/([0-9a-f-]{36})-([0-9a-f]{" + SIGNATURE_LENGTH + "})"
            + "\\.(pdf|jpg|png|webp)$");

    private final SecretKeySpec signingKey;

    public OnlineTestAnswerKeys(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM);
    }

    public boolean isSupportedType(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType);
    }

    /** online-tests/2026/06/{uuid}-{서명}.pdf */
    public String issue(Long teacherId, String contentType, LocalDate today) {
        String extension = EXTENSIONS.get(contentType);
        String uuid = UUID.randomUUID().toString();
        return "%s/%d/%02d/%s-%s.%s".formatted(
            PREFIX, today.getYear(), today.getMonthValue(), uuid,
            sign(teacherId, uuid, extension), extension);
    }

    /** 형식과 서명이 모두 맞아야 통과한다. 서명은 시간 상수 비교로 대조한다. */
    public boolean matches(String s3Key, Long teacherId) {
        if (s3Key == null) {
            return false;
        }
        Matcher matcher = KEY_PATTERN.matcher(s3Key);
        if (!matcher.matches()) {
            return false;
        }
        String expected = sign(teacherId, matcher.group(3), matcher.group(5));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            matcher.group(4).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(Long teacherId, String uuid, String extension) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            byte[] digest = mac.doFinal(
                (teacherId + ":" + uuid + ":" + extension).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, SIGNATURE_LENGTH);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("s3Key 서명에 실패했습니다.", e);
        }
    }
}
