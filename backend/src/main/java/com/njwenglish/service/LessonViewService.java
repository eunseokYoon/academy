package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.response.PageResponse;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.YoutubeUrls;
import com.njwenglish.dto.lesson.LessonReportListItemResponse;
import com.njwenglish.dto.lesson.LessonReportResponse;
import com.njwenglish.dto.lesson.LessonViewRequest;
import com.njwenglish.dto.lesson.LessonViewsResponse;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonView;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.LessonViewRepository;
import com.njwenglish.repository.SubmissionRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-5 수업 레포트와 영상, P-5 학부모 조회.
 *
 * <p><b>영상은 학생만 본다.</b> 학부모는 같은 수업의 레포트(내용·중점·다음 예고·숙제)를
 * 보되 영상은 못 본다. 응답 DTO는 하나를 같이 쓰고 학부모 쪽 영상 값이 null이다.
 *
 * <p>그래서 <b>DTO를 new로 직접 만들지 마라.</b> LessonReportResponse.forStudent /
 * forParent 두 팩토리만 쓴다. forParent는 영상을 인자로 받지도 않아서 채울 방법이 없다 —
 * 여기서 null을 손으로 넘기기 시작하면 언젠가 한 곳에서 값이 들어간다.
 *
 * <p>조회 조건 네 가지가 항상 함께 간다: 본인 · 소속 반 · 공개됨 · 재원 기간 안.
 * 마지막 조건이 빠지면 5월에 입반한 학생이 3월 수업 영상을 본다.
 * 조건은 LessonRepository.findForStudent 한 곳에 모아 뒀다.
 */
@Service
@RequiredArgsConstructor
public class LessonViewService {

    /** 최근 7일 안에 공개된 수업에 NEW를 붙인다. */
    private static final int NEW_DAYS = 7;

    private final LessonRepository lessonRepository;
    private final LessonViewRepository lessonViewRepository;
    private final HomeworkRepository homeworkRepository;
    private final SubmissionRepository submissionRepository;
    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional(readOnly = true)
    public PageResponse<LessonReportListItemResponse> myLessons(Short year, Short month,
                                                                Short week, Pageable pageable) {
        Student me = studentAccessGuard.requireSelf();
        Page<Lesson> lessons = lessonRepository.findForStudent(me.getId(), year, month, week,
            pageable);

        List<Long> lessonIds = lessons.getContent().stream().map(Lesson::getId).toList();
        Set<Long> viewed = lessonIds.isEmpty() ? Set.of()
            : Set.copyOf(lessonViewRepository.findViewedLessonIds(me.getId(), lessonIds));
        Map<Long, Homework> homeworks = firstHomeworkByLesson(lessonIds);
        OffsetDateTime newSince = OffsetDateTime.now().minusDays(NEW_DAYS);

        return PageResponse.from(lessons.map(lesson -> {
            Homework homework = homeworks.get(lesson.getId());
            return LessonReportListItemResponse.forStudent(
                lesson.getId(),
                lesson.getLessonDate(),
                lesson.getTitle(),
                lesson.getClassRoom().getName(),
                YoutubeUrls.videoId(lesson.getVideoUrl()) != null,
                lesson.getPublishedAt().isAfter(newSince),
                viewed.contains(lesson.getId()),
                homework == null ? null : homework.getTitle());
        }));
    }

    @Transactional(readOnly = true)
    public LessonReportResponse myLesson(Long lessonId) {
        Student me = studentAccessGuard.requireSelf();
        Lesson lesson = findAccessible(lessonId, me.getId());

        String videoId = YoutubeUrls.videoId(lesson.getVideoUrl());
        Homework homework = firstHomeworkByLesson(List.of(lesson.getId())).get(lesson.getId());

        return LessonReportResponse.forStudent(
            lesson.getId(),
            lesson.getLessonDate(),
            lesson.getTitle(),
            lesson.getClassRoom().getName(),
            videoId,
            YoutubeUrls.embedUrl(videoId),
            lesson.getContent(),
            lesson.getKeyPoints(),
            lesson.getNextPreview(),
            homework == null ? null : toHomework(homework, me.getId()),
            attendanceRepository.findByLessonIdAndStudentId(lesson.getId(), me.getId())
                .map(a -> a.getStatus())
                .orElse(null));
    }

    /**
     * P-5 목록. <b>첫 줄이 requireAccessible이다</b> — 학부모 A가 학부모 B의 자녀
     * studentId를 넣으면 여기서 403이다.
     *
     * <p>조회 조건은 학생 목록과 같은 findForStudent를 그대로 쓴다. 재원 기간 밖 수업과
     * 미공개 수업을 거르는 조건이 거기 다 들어 있어서, 따로 짜면 그중 하나를 빠뜨린다.
     */
    @Transactional(readOnly = true)
    public PageResponse<LessonReportListItemResponse> childLessons(Long studentId, Short year,
                                                                   Short month, Short week,
                                                                   Pageable pageable) {
        Student child = studentAccessGuard.requireAccessible(studentId);
        Page<Lesson> lessons = lessonRepository.findForStudent(child.getId(), year, month, week,
            pageable);

        Map<Long, Homework> homeworks = firstHomeworkByLesson(
            lessons.getContent().stream().map(Lesson::getId).toList());
        OffsetDateTime newSince = OffsetDateTime.now().minusDays(NEW_DAYS);

        return PageResponse.from(lessons.map(lesson -> {
            Homework homework = homeworks.get(lesson.getId());
            // hasVideo·viewed는 팩토리가 null로 채운다. 여기서 넘길 방법 자체가 없다
            return LessonReportListItemResponse.forParent(
                lesson.getId(),
                lesson.getLessonDate(),
                lesson.getTitle(),
                lesson.getClassRoom().getName(),
                lesson.getPublishedAt().isAfter(newSince),
                homework == null ? null : homework.getTitle());
        }));
    }

    /**
     * P-5 상세. <b>첫 줄이 requireAccessible이다.</b>
     *
     * <p>영상은 내려주지 않는다. videoUrl을 파싱하지도 않는다 —
     * forParent 팩토리가 videoId·embedUrl을 인자로 받지 않아서 넘길 방법이 없다.
     */
    @Transactional(readOnly = true)
    public LessonReportResponse childLesson(Long studentId, Long lessonId) {
        Student child = studentAccessGuard.requireAccessible(studentId);
        Lesson lesson = findAccessible(lessonId, child.getId());
        Homework homework = firstHomeworkByLesson(List.of(lesson.getId())).get(lesson.getId());

        return LessonReportResponse.forParent(
            lesson.getId(),
            lesson.getLessonDate(),
            lesson.getTitle(),
            lesson.getClassRoom().getName(),
            lesson.getContent(),
            lesson.getKeyPoints(),
            lesson.getNextPreview(),
            homework == null ? null : toParentHomework(homework, child.getId()),
            attendanceRepository.findByLessonIdAndStudentId(lesson.getId(), child.getId())
                .map(a -> a.getStatus())
                .orElse(null));
    }

    /**
     * 시청 기록. upsert라 행이 중복되지 않고 watchSeconds는 누적된다.
     * first_viewed_at은 최초 1회만 기록되고 이후 호출에서 갱신되지 않는다.
     */
    @Transactional
    public void recordView(Long lessonId, LessonViewRequest request) {
        Student me = studentAccessGuard.requireSelf();
        findAccessible(lessonId, me.getId());
        lessonViewRepository.upsert(lessonId, me.getId(), request.watchSeconds());
    }

    /**
     * T-4 시청 현황. <b>미시청 학생도 포함한다.</b> 선생님이 확인하려는 건 안 본 학생이다.
     *
     * <p>명단은 수업일 기준 재원생이다. 오늘 기준으로 뽑으면 그 뒤 퇴원한 학생이 빠지고,
     * 그 뒤 입반한 학생이 "안 봤다"로 잡힌다.
     */
    @Transactional(readOnly = true)
    public LessonViewsResponse views(Long lessonId) {
        Lesson lesson = lessonRepository.findWithClassRoom(lessonId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Student> students = enrollmentRepository.findActiveStudents(
            lesson.getClassRoom().getId(), lesson.getLessonDate());
        Map<Long, LessonView> views = lessonViewRepository.findByLessonId(lessonId).stream()
            .collect(Collectors.toMap(v -> v.getStudent().getId(), Function.identity()));

        List<LessonViewsResponse.Item> items = students.stream()
            .map(student -> {
                LessonView view = views.get(student.getId());
                return new LessonViewsResponse.Item(
                    student.getId(),
                    student.getName(),
                    view != null,
                    view == null ? null : view.getFirstViewedAt(),
                    view == null ? 0 : view.getWatchSeconds());
            })
            .toList();

        int viewedCount = (int) items.stream().filter(LessonViewsResponse.Item::viewed).count();
        return new LessonViewsResponse(lessonId, items.size(), viewedCount, items);
    }

    // ---------- 내부 ----------

    /** 조회 조건을 통과하지 못하면 404다. 남의 반 수업인지 미공개인지 구분해 알려주지 않는다. */
    private Lesson findAccessible(Long lessonId, Long studentId) {
        return lessonRepository.findForStudent(lessonId, studentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 한 수업에 숙제가 둘 이상일 수 있다. 마감이 이른 것 하나만 붙인다. */
    private Map<Long, Homework> firstHomeworkByLesson(List<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Homework> byLesson = new HashMap<>();
        for (Homework homework : homeworkRepository.findByLessonIds(lessonIds)) {
            byLesson.putIfAbsent(homework.getLesson().getId(), homework);
        }
        return byLesson;
    }

    /**
     * 숙제 출제 시 대상 전원의 submissions를 미리 만들지만, 그 뒤에 입반한 학생은 행이 없다.
     * 없으면 미제출로 본다.
     */
    private LessonReportResponse.Homework toHomework(Homework homework, Long studentId) {
        return LessonReportResponse.Homework.forStudent(
            homework.getId(),
            homework.getTitle(),
            homework.getDescription(),
            homework.getKind(),
            homework.getDueAt(),
            submissionOf(homework.getId(), studentId));
    }

    /** 학부모용. <b>description을 넘기지 않는다</b> — 팩토리가 인자로 받지도 않는다. */
    private LessonReportResponse.Homework toParentHomework(Homework homework, Long studentId) {
        return LessonReportResponse.Homework.forParent(
            homework.getId(),
            homework.getTitle(),
            homework.getKind(),
            homework.getDueAt(),
            submissionOf(homework.getId(), studentId));
    }

    /**
     * 제출물 자체를 넘긴다. <b>status만 뽑아 쓰면 안 된다</b> —
     * 이제 수업에 걸리는 숙제는 대개 그리드 열이고, 거기서 ⭕를 받은 학생은
     * 온라인 제출을 하지 않아 status가 영원히 NOT_SUBMITTED다.
     * status만 보면 숙제를 다 해온 학생의 레포트에 "미제출"이 뜬다.
     *
     * <p>없을 수 있다 — 출제 뒤에 입반한 학생은 칸이 없다.
     */
    private Submission submissionOf(Long homeworkId, Long studentId) {
        return submissionRepository.findByHomeworkAndStudent(homeworkId, studentId)
            .orElse(null);
    }
}
