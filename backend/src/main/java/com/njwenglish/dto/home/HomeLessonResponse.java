package com.njwenglish.dto.home;

import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.entity.Lesson;
import java.time.LocalDate;

/**
 * S-1 홈의 지난 수업 카드. <b>학생 전용이다</b> — 학부모 홈에 붙이지 마라.
 *
 * <p>S-5 상세({@link com.njwenglish.dto.lesson.LessonReportResponse})를 재사용하지 않는다.
 * 그쪽은 숙제와 출석 상태까지 담는데 홈 카드는 그리지 않는다 — 재사용하면 화면에 안 나올
 * 값을 위해 쿼리가 늘어난다. 필드 이름은 그쪽과 맞춰 뒀으니 헷갈리면 그 문서를 봐라.
 *
 * <p>videoId·embedUrl은 저장하지 않고 videoUrl에서 파싱한다. 프론트가 URL 형식을
 * 판단하지 않게 하려는 것이다. <b>영상은 홈에서 재생하지 않는다</b> — embedUrl이 있으면
 * 상세(S-5)로 보내는 버튼만 그린다. 홈에 iframe을 심으면 첫 화면이 그만큼 무거워진다.
 */
public record HomeLessonResponse(Long lessonId,
                                 LocalDate lessonDate,
                                 String title,
                                 String videoId,
                                 String embedUrl,
                                 String content,
                                 String nextPreview) {

    public static HomeLessonResponse from(Lesson lesson) {
        String videoId = YoutubeUrls.videoId(lesson.getVideoUrl());
        return new HomeLessonResponse(
            lesson.getId(), lesson.getLessonDate(), lesson.getTitle(),
            videoId, YoutubeUrls.embedUrlOf(lesson.getVideoUrl()),
            lesson.getContent(), lesson.getNextPreview());
    }
}
