package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.lesson.LessonViewRequest;
import com.njwenglish.dto.lesson.LessonViewsResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonView;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.LessonViewRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LessonViewServiceTest {

    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 5, 20);

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonViewRepository lessonViewRepository;
    @Mock
    private HomeworkRepository homeworkRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private LessonViewService lessonViewService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Student me = Fixtures.student(88L, "서동환");

    @BeforeEach
    void setUp() {
        lessonViewService = new LessonViewService(lessonRepository, lessonViewRepository,
            homeworkRepository, submissionRepository, attendanceRepository, enrollmentRepository,
            studentAccessGuard);
    }

    private Lesson publishedLesson() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LESSON_DATE);
        lesson.publish(OffsetDateTime.now().minusDays(1));
        return lesson;
    }

    @Test
    @DisplayName("미공개·재원 기간 밖 수업은 조회 자체가 되지 않아 404다")
    void 접근할_수_없는_수업은_404다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);
        // findForStudent가 published_at·재원 기간 조건을 이미 담고 있다.
        // 조건에서 걸린 수업은 비어 있는 Optional로 돌아온다
        given(lessonRepository.findForStudent(501L, 88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> lessonViewService.myLesson(501L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("시청 기록은 upsert로 남긴다 — watch_seconds를 덮어쓰지 않고 누적한다")
    void 시청_기록은_upsert된다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(lessonRepository.findForStudent(501L, 88L))
            .willReturn(Optional.of(publishedLesson()));

        lessonViewService.recordView(501L, new LessonViewRequest(30));

        verify(lessonViewRepository).upsert(501L, 88L, 30);
        verify(lessonViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("접근할 수 없는 수업에는 시청 기록을 남기지 않는다")
    void 접근_불가_수업은_시청_기록을_남기지_않는다() {
        given(studentAccessGuard.requireSelf()).willReturn(me);
        given(lessonRepository.findForStudent(501L, 88L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> lessonViewService.recordView(501L, new LessonViewRequest(30)))
            .isInstanceOf(BusinessException.class);

        verify(lessonViewRepository, never()).upsert(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("선생님 시청 현황에 미시청 학생도 포함된다 — 보려는 건 안 본 학생이다")
    void 미시청_학생도_포함된다() {
        Student viewer = Fixtures.student(88L, "서동환");
        Student absent = Fixtures.student(91L, "김하늘");
        given(lessonRepository.findWithClassRoom(501L))
            .willReturn(Optional.of(publishedLesson()));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(viewer, absent));
        given(lessonViewRepository.findByLessonId(501L))
            .willReturn(List.of(lessonView(viewer, 1820)));

        LessonViewsResponse response = lessonViewService.views(501L);

        assertThat(response.totalStudents()).isEqualTo(2);
        assertThat(response.viewedCount()).isEqualTo(1);
        assertThat(response.items()).extracting(LessonViewsResponse.Item::studentId,
                LessonViewsResponse.Item::viewed, LessonViewsResponse.Item::watchSeconds)
            .containsExactly(tuple(88L, true, 1820), tuple(91L, false, 0));
    }

    /** lesson_views는 감사 컬럼이 없어 팩토리도 없다. 조회 결과 흉내만 낸다. */
    private LessonView lessonView(Student student, int watchSeconds) {
        LessonView view = BeanUtils.instantiateClass(LessonView.class);
        ReflectionTestUtils.setField(view, "student", student);
        ReflectionTestUtils.setField(view, "watchSeconds", watchSeconds);
        ReflectionTestUtils.setField(view, "firstViewedAt", OffsetDateTime.now());
        return view;
    }
}
