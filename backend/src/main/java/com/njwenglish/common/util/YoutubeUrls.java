package com.njwenglish.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube 미등록(unlisted) 링크에서 videoId만 뽑는다. 영상 파일·트랜스코딩·스트리밍은
 * 범위 밖이고, 서버가 저장하는 것은 URL 문자열뿐이다.
 *
 * <p>선생님이 주소창에서 복사해 오는 형식이 세 가지라 전부 받아야 한다. 프론트는
 * embedUrl로 iframe에 넣는다.
 */
public final class YoutubeUrls {

    /** youtu.be/{id} · youtube.com/watch?v={id} · youtube.com/embed/{id} */
    private static final Pattern VIDEO_ID = Pattern.compile(
        "^(?:https?://)?(?:www\\.|m\\.)?(?:"
            + "youtu\\.be/([\\w-]{11})"
            + "|youtube\\.com/watch\\?(?:[^&]*&)*v=([\\w-]{11})"
            + "|youtube\\.com/embed/([\\w-]{11})"
            + ")(?:[?&#].*)?$");

    private static final String EMBED_PREFIX = "https://www.youtube.com/embed/";

    private YoutubeUrls() {
    }

    /** 파싱할 수 없으면 null. 호출부에서 VALIDATION_FAILED로 바꾼다. */
    public static String videoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = VIDEO_ID.matcher(url.trim());
        if (!matcher.matches()) {
            return null;
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        return null;
    }

    public static String embedUrl(String videoId) {
        return videoId == null ? null : EMBED_PREFIX + videoId;
    }
}
