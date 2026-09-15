package com.njwenglish.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube 미등록(unlisted) 링크에서 videoId 또는 재생목록 id를 뽑는다. 영상 파일·트랜스코딩·
 * 스트리밍은 범위 밖이고, 서버가 저장하는 것은 URL 문자열뿐이다.
 *
 * <p>선생님이 주소창에서 복사해 오는 형식이 세 가지(영상)와 재생목록 하나라 전부 받아야 한다.
 * 프론트는 embedUrlOf로 iframe에 넣는다.
 */
public final class YoutubeUrls {

    /** youtu.be/{id} · youtube.com/watch?v={id} · youtube.com/embed/{id} */
    private static final Pattern VIDEO_ID = Pattern.compile(
        "^(?:https?://)?(?:www\\.|m\\.)?(?:"
            + "youtu\\.be/([\\w-]{11})"
            + "|youtube\\.com/watch\\?(?:[^&]*&)*v=([\\w-]{11})"
            + "|youtube\\.com/embed/([\\w-]{11})"
            + ")(?:[?&#].*)?$");

    /**
     * youtube.com/playlist?list={id} · watch?v={vid}&list={id} · youtu.be/{vid}?list={id}
     *
     * <p><b>youtu.be 단축 링크도 받아야 한다.</b> YouTube 모바일 앱 공유 시트가 재생목록 안의
     * 영상을 {@code youtu.be/VIDEOID?list=PLxxx&index=2}로 준다. 이 형식을 빼면 VIDEO_ID
     * 패턴에 걸려 <b>영상 하나만</b> 임베드되는데, 저장도 재생도 되므로 아무도 모르는 채
     * 학생이 그날 수업 영상 중 하나만 본다.
     */
    private static final Pattern PLAYLIST_ID = Pattern.compile(
        "^(?:https?://)?(?:www\\.|m\\.)?(?:"
            + "youtube\\.com/(?:playlist|watch|embed/[\\w-]+)"
            + "|youtu\\.be/[\\w-]{11}"
            + ")\\?(?:[^&]*&)*list=([\\w-]+)(?:[&#].*)?$");

    private static final String EMBED_PREFIX = "https://www.youtube.com/embed/";
    private static final String PLAYLIST_PREFIX = "https://www.youtube.com/embed/videoseries?list=";

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

    /** 파싱할 수 없으면 null. */
    public static String playlistId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = PLAYLIST_ID.matcher(url.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * 임베드 주소. <b>재생목록이 단일 영상보다 우선한다</b>(2026-09-01) —
     * 선생님이 그날 찍은 영상 여러 개를 재생목록으로 올리므로,
     * watch?v=...&list=... 형식이면 영상 하나가 아니라 목록 전체를 보여야 한다.
     *
     * <p>못 읽으면 null이다. 호출부에서 VALIDATION_FAILED로 바꾼다.
     */
    public static String embedUrlOf(String url) {
        String playlistId = playlistId(url);
        String videoId = videoId(url);
        /*
         * 영상 ID와 재생목록이 둘 다 있으면 embed/{videoId}?list={listId}다.
         *
         * videoseries 형식은 재생목록을 공개적으로 조회할 수 있어야 동작한다.
         * 선생님이 수업 영상을 「일부 공개」 재생목록에 담으면 그 조회가 막혀서
         * 플레이어가 "이 동영상은 볼 수 없습니다"를 띄운다 — 안의 영상은 멀쩡한데도 그렇다.
         * 영상 ID를 앞에 두면 그 영상으로 플레이어를 열고 재생목록은 이어보기 맥락으로만
         * 쓰므로 일부 공개에서도 재생된다(2026-09-04 확인).
         *
         * 그래서 선생님에게는 재생목록 안의 영상 링크를 붙이라고 안내한다.
         * playlist?list=만 있는 링크는 영상 ID가 없어 videoseries로 갈 수밖에 없고,
         * 그건 공개 재생목록에서만 된다.
         */
        if (videoId != null && playlistId != null) {
            return EMBED_PREFIX + videoId + "?list=" + playlistId;
        }
        if (playlistId != null) {
            return PLAYLIST_PREFIX + playlistId;
        }
        return videoId == null ? null : EMBED_PREFIX + videoId;
    }
}
