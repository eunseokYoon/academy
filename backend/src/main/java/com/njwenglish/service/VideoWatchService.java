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
import com.njwenglish.repository.AttendanceRepository;
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
 * <p><b>결석인 학생이 {@link #ONLINE_THRESHOLD}% 이상 보면 출결이 자동으로 온라인이 된다.</b>
 * 선생님이 온라인을 되돌렸으면 다시 바뀌지 않는다({@code Attendance.onlineAutoBlocked}).
 * 출석·지각 등 이미 온 학생은 영상을 봐도 그대로다.
 */
@Service
@RequiredArgsConstructor
public class VideoWatchService {

    /** 온라인 출결 기준(%). 학부모의 「시청 완료」도 이 값이다. */
    public static final int ONLINE_THRESHOLD = 80;

    private final LessonRepository lessonRepository;
    private final LessonVideoWatchRepository watchRepository;
    private final AttendanceRepository attendanceRepository;
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

        Integer percent = percentOf(lesson, watchRepository.findByLessonIdAndStudentId(
            lessonId, me.getId()));
        if (percent != null && percent >= ONLINE_THRESHOLD) {
            attendanceRepository.findByLessonIdAndStudentId(lessonId, me.getId())
                .ifPresent(attendance -> attendance.markOnlineByWatch());
        }
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
