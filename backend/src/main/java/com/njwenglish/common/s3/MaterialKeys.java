package com.njwenglish.common.s3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 공지 첨부 파일의 s3Key 발급·대조. {@link SubmissionMediaKeys}와 같은 방식이다 —
 * 발급한 키에 서명을 박아 두고 등록 시 대조해서, 클라이언트가 버킷 내 임의 경로를
 * 첨부로 등록하는 것을 막는다.
 *
 * <p><b>서명은 선생님 id에 묶는다.</b> 제출물과 달리 등록 시점에 자료 행이 아직 없고,
 * 같은 파일을 여러 반에 줄 때 하나의 s3Key로 여러 행을 만들어야 하기 때문이다
 * (공지가 만들어지기 전에 올라가므로 묶을 공지가 없다).
 *
 * <p><b>허용 목록은 확장자 기준이다.</b> hwp·hwpx·zip은 브라우저가 보내는 MIME이
 * 제각각이라 Content-Type으로 걸러낼 수 없다. 대신 presign에 쓸 Content-Type을
 * 서버가 확장자에서 정해 내려주고, 클라이언트는 그 값으로 PUT한다.
 *
 * <p><b>exe·sh·bat·js·html은 목록에 없다.</b> HTML은 저장 후 서빙 시 XSS 경로가 된다.
 * 다운로드가 항상 Content-Disposition: attachment라도 목록을 늘리지 마라.
 *
 * <p><b>클래스명과 materials/ 프리픽스는 자료실 시절 그대로다.</b> 바꾸지 마라 —
 * "materials/에는 S3 수명주기를 걸지 않는다"는 규칙이 이 프리픽스에 걸려 있다.
 */
@Component
public class MaterialKeys {

    /** 문서·압축 파일은 브라우저에서 줄일 방법이 없다. 50MB가 상한이다. */
    public static final long MAX_BYTES = 50L * 1024 * 1024;

    private static final String PREFIX = "materials";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_LENGTH = 16;

    /** 확장자 → presign에 쓸 Content-Type. 이 맵의 키가 곧 허용 목록이다. */
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
        Map.entry("pdf", "application/pdf"),
        Map.entry("hwp", "application/x-hwp"),
        Map.entry("hwpx", "application/hwp+zip"),
        Map.entry("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("pptx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        Map.entry("zip", "application/zip"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("png", "image/png"));

    private static final Pattern KEY_PATTERN = Pattern.compile(
        "^materials/(\\d{4})/(\\d{2})/([0-9a-f-]{36})-([0-9a-f]{" + SIGNATURE_LENGTH + "})"
            + "\\.(pdf|hwp|hwpx|docx|xlsx|pptx|zip|jpg|png)$");

    private final SecretKeySpec signingKey;

    public MaterialKeys(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
            HMAC_ALGORITHM);
    }

    /**
     * 파일명에서 확장자를 뽑는다. 허용 목록에 없으면 null이고 호출부가 400으로 돌려준다.
     *
     * <p>{@code jpeg}는 {@code jpg}로 접는다. 맥·아이폰에서 고른 사진이 .jpeg로 오는 일이
     * 흔한데, 그것 때문에 업로드가 막히면 선생님은 이유를 알 수 없다.
     * 원본 파일명은 materials.file_name에 그대로 남으므로 다운로드 이름은 바뀌지 않는다.
     */
    public String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if ("jpeg".equals(extension)) {
            extension = "jpg";
        }
        return CONTENT_TYPES.containsKey(extension) ? extension : null;
    }

    public String contentTypeOf(String extension) {
        return CONTENT_TYPES.get(extension);
    }

    /** materials/2026/05/{uuid}-{서명}.pdf */
    public String issue(Long teacherId, String extension, LocalDate today) {
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
