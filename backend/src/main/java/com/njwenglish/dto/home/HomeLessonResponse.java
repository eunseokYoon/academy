package com.njwenglish.dto.home;

import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonVideo;
import java.time.LocalDate;

/**
 * S-1 홈의 지난 수업 카드. <b>학생 전용이다</b> — 학부모 홈에 붙이지 마라.
 *
 * <p>S-5 상세({@link com.njwenglish.dto.lesson.LessonReportResponse})를 재사용하지 않는다.
 * 그쪽은 숙제와 출석 상태까지 담는데 홈 카드는 그리지 않는다 — 재사용하면 화면에 안 나올
 * 값을 위해 쿼리가 늘어난다. 필드 이름은 그쪽과 맞춰 뒀으니 헷갈리면 그 문서를 봐라.
 *
 * <p>수업 영상은 여러 개일 수 있다(2026-09-04). 홈 카드는 <b>첫 영상만</b> 쓰고
 * 개수를 videoCount로 알린다 — 카드 하나에 목록을 그리면 첫 화면이 길어진다.
 * videoId는 썸네일용이라 첫 영상이 재생목록 링크면 null이다.
 *
 * <p><b>영상은 홈에서 재생하지 않는다</b> — 있으면 상세(S-5)로 보내는 버튼만 그린다.
 * 홈에 iframe을 심으면 첫 화면이 그만큼 무거워진다.
 */
public record HomeLessonResponse(Long lessonId,
                                 LocalDate lessonDate,
                                 String title,
                                 String videoId,
                                 /** 첫 영상. 없으면 null이라 프론트가 영역을 숨긴다. */
                                 String embedUrl,
                                 int videoCount,
                                 String content,
                                 String homeworkNote) {

    public static HomeLessonResponse from(Lesson lesson) {
        LessonVideo first = lesson.getVideos().isEmpty() ? null : lesson.getVideos().get(0);
        return new HomeLessonResponse(
            lesson.getId(), lesson.getLessonDate(), lesson.getTitle(),
            first == null ? null : YoutubeUrls.videoId(first.getUrl()),
            first == null ? null : YoutubeUrls.embedUrlOf(first.getUrl()),
            lesson.getVideos().size(),
            lesson.getContent(), lesson.getHomeworkNote());
    }
}
