package com.njwenglish.dto.lesson;

import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.entity.LessonVideo;

/**
 * 영상 한 줄. <b>videoId·embedUrl은 저장하지 않고 url에서 파싱한다</b> —
 * 프론트가 URL 형식을 알 필요가 없다.
 *
 * <p>videoId는 썸네일에만 쓴다. 재생목록 링크면 null이고, 그때는 embedUrl만 있다.
 */
public record LessonVideoResponse(String title, String videoId, String embedUrl) {

    public static LessonVideoResponse from(LessonVideo video) {
        return new LessonVideoResponse(video.getTitle(),
            YoutubeUrls.videoId(video.getUrl()), YoutubeUrls.embedUrlOf(video.getUrl()));
    }
}
