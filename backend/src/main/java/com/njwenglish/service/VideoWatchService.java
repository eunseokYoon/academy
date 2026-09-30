package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.dto.lesson.LessonWatchRequest;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonVideo;
import com.njwenglish.entity.LessonVideoWatch;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.LessonVideoWatchRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수업 영상 시청 기록(V28, 2026-09-29 사용자 결정). CLAUDE.md 의 「영상 시청 기록」 절을 봐라.
 *
 * <p>수업 시청률은 <b>그 수업의 지금 영상들 시청률의 평균</b>이다. 안 연 영상은 0%다.
 * 영상이 없는 수업은 null — 0%가 아니다(볼 것이 없었다).
 *
 * <p><b>출결을 바꾸지 않는다(2026-09-30 사용자 결정).</b> 온라인 출석은 선생님이 T-5 에서
 * 시청률을 보고 직접 고른다. 결석 + 80% 이면 자동으로 온라인이 되던 규칙(09-29)은 없앴다 —
 * 자동으로 바꾸면 선생님이 되돌린 값을 기억할 장치({@code online_auto_blocked})와 열어 둔 명단의
 * 옛 값을 가려낼 장치가 따라붙고, 그래도 선생님이 판단할 여지가 사라진다.
 */
@Service
@RequiredArgsConstructor
public class VideoWatchService {

    /** 학부모에게 「시청 완료」로 보이는 기준(%). 출결은 바꾸지 않는다(클래스 주석). */
    public static final int WATCHED_THRESHOLD = 80;

    private final LessonRepository lessonRepository;
    private final LessonVideoWatchRepository watchRepository;
    private final StudentAccessGuard studentAccessGuard;

    /**
     * 학생의 재생 보고. 그 수업을 볼 수 있는 학생만(미공개·남의 반 수업은 404).
     * 수업이 고쳐져 그 영상이 없어졌으면 조용히 버린다 — 학생에게 오류를 띄울 일이 아니다.
     */
    @Transactional
    public void record(Long lessonId, LessonWatchRequest request) {
        Student me = studentAccessGuard.requireSelf();
        Lesson lesson = lessonRepository.findForStudent(lessonId, me.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LessonVideo video = lesson.getVideos().stream()
            .filter(v -> request.embedUrl().equals(YoutubeUrls.embedUrlOf(v.getUrl())))
            .findFirst()
            .orElse(null);
        if (video == null) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int bucketCount = LessonVideoWatch.bucketsOf(request.durationSeconds());
        LessonVideoWatch watch = watchRepository
            .findByLessonIdAndStudentIdAndVideoUrl(lessonId, me.getId(), video.getUrl())
            .orElseGet(() -> watchRepository.save(
                LessonVideoWatch.start(lesson, me, video.getUrl(), bucketCount, now)));
        watch.markWatched(request.buckets(), bucketCount, now);
    }

    /** 한 학생의 수업 시청률. 영상이 없는 수업이면 null. */
    @Transactional(readOnly = true)
    public Integer lessonPercent(Lesson lesson, Long studentId) {
        return percentOf(lesson, watchRepository.findByLessonIdAndStudentId(
            lesson.getId(), studentId));
    }

    /** T-5 명단용. 학생 id → 시청률. 영상이 없는 수업이면 빈 맵이다. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> lessonPercents(Lesson lesson) {
        if (lesson.getVideos().isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LessonVideoWatch>> byStudent = watchRepository
            .findByLessonId(lesson.getId()).stream()
            .collect(Collectors.groupingBy(w -> w.getStudent().getId()));
        Map<Long, Integer> percents = new HashMap<>();
        byStudent.forEach((studentId, watches) ->
            percents.put(studentId, percentOf(lesson, watches)));
        return percents;
    }

    /** 영상별 시청률의 평균. 안 연 영상은 0%. */
    static Integer percentOf(Lesson lesson, List<LessonVideoWatch> watches) {
        List<LessonVideo> videos = lesson.getVideos();
        if (videos.isEmpty()) {
            return null;
        }
        Map<String, Integer> byUrl = watches.stream()
            .collect(Collectors.toMap(LessonVideoWatch::getVideoUrl, LessonVideoWatch::percent,
                Math::max));
        int sum = 0;
        for (LessonVideo video : videos) {
            sum += byUrl.getOrDefault(video.getUrl(), 0);
        }
        return sum / videos.size();
    }
}
