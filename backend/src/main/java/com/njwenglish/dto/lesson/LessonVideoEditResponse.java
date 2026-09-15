package com.njwenglish.dto.lesson;

import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.entity.LessonVideo;

/**
 * 선생님 화면(T-4)용 영상 한 줄. 학생용({@link LessonVideoResponse})과 달리
 * <b>원본 url을 담는다</b> — 수정할 때 입력칸에 되돌려 넣어야 하기 때문이다.
 *
 * <p>학생·학부모 응답에 이 레코드를 쓰지 마라. 원본 링크가 필요 없는 쪽에
 * 굳이 내보낼 이유가 없다.
 */
public record LessonVideoEditResponse(String url, String title, String embedUrl) {

    public static LessonVideoEditResponse from(LessonVideo video) {
        return new LessonVideoEditResponse(video.getUrl(), video.getTitle(),
            YoutubeUrls.embedUrlOf(video.getUrl()));
    }
}
