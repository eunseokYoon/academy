package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.dto.homework.HomeworkCreateRequest;
import com.njwenglish.dto.homework.HomeworkCreateResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HomeworkServiceTest {

    @Mock
    private HomeworkRepository homeworkRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private SubmissionPhotoRepository submissionPhotoRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private HomeworkTemplateService templateService;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;

    private HomeworkService homeworkService;

    private static final OffsetDateTime DUE_AT =
        OffsetDateTime.of(2026, 5, 21, 20, 0, 0, 0, ZoneOffset.ofHours(9));

    private final Teacher teacher = Fixtures.teacherEntity(1L);
    private ClassRoom classRoom;

    @BeforeEach
    void setUp() {
        classRoom = ClassRoom.create(teacher, "고2 심화반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);

        homeworkService = new HomeworkService(homeworkRepository, submissionRepository,
            submissionPhotoRepository, classRoomRepository, lessonRepository,
            enrollmentRepository, teacherRepository, templateService, presignedUrlProvider);
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("출제 시 재원생 전원의 submission이 NOT_SUBMITTED로 생성된다")
    void 출제_시_재원생_전원의_submission이_생성된다() {
        List<Student> students = List.of(
            Fixtures.student(88L, "서동환"),
            Fixtures.student(91L, "김하늘"),
            Fixtures.student(97L, "박서준"));
        givenTeacherAndClassRoom();
        given(enrollmentRepository.findActiveStudents(3L, LocalDate.of(2026, 5, 21)))
            .willReturn(students);
        givenSavedHomework(720L);

        HomeworkCreateResponse response = homeworkService.create(request(null, null, false));

        assertThat(response.homeworkId()).isEqualTo(720L);
        assertThat(response.targetCount()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Submission>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .hasSize(3)
            .allSatisfy(submission ->
                assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.NOT_SUBMITTED));
    }

    @Test
    @DisplayName("대상은 마감일 기준 재원생이다 — 오늘 기준이 아니다")
    void 대상은_마감일_기준_재원생이다() {
        givenTeacherAndClassRoom();
        given(enrollmentRepository.findActiveStudents(anyLong(), any())).willReturn(List.of());
        givenSavedHomework(720L);

        homeworkService.create(request(null, null, false));

        // 오늘이 아니라 마감일(2026-05-21)로 조회해야 그때 다니는 학생이 대상이 된다
        verify(enrollmentRepository).findActiveStudents(3L, LocalDate.of(2026, 5, 21));
    }

    @Test
    @DisplayName("템플릿을 사용하면 use_count가 증가한다")
    void 템플릿_사용_시_use_count가_증가한다() {
        givenTeacherAndClassRoom();
        given(enrollmentRepository.findActiveStudents(anyLong(), any())).willReturn(List.of());
        givenSavedHomework(720L);

        homeworkService.create(request(null, 5L, false));

        verify(templateService).increaseUseCount(5L);
        verify(templateService, never()).save(any(), any());
    }

    @Test
    @DisplayName("제출한 학생이 있는 숙제는 삭제할 수 없다")
    void 제출한_학생이_있는_숙제는_삭제할_수_없다() {
        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(homework(720L)));
        given(submissionRepository.countGradedOrSubmitted(720L)).willReturn(1L);

        assertThatThrownBy(() -> homeworkService.delete(720L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SUBMISSION_EXISTS);

        verify(homeworkRepository, never()).delete(any());
    }

    @Test
    @DisplayName("전원 미제출인 숙제는 submissions를 먼저 지우고 삭제된다")
    void 전원_미제출인_숙제는_submissions와_함께_삭제된다() {
        Homework homework = homework(720L);
        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(homework));
        given(submissionRepository.countGradedOrSubmitted(720L)).willReturn(0L);
        given(submissionRepository.findByHomeworkForTeacher(720L)).willReturn(List.of());

        homeworkService.delete(720L);

        // submissions.homework_id에는 CASCADE가 없다. 먼저 지우지 않으면 FK 위반이다
        verify(submissionRepository).deleteByHomeworkId(720L);
        verify(homeworkRepository).delete(homework);
    }

    @Test
    @DisplayName("마감을 앞당기는 수정은 거부된다")
    void 마감을_앞당기는_수정은_거부된다() {
        Homework homework = homework(720L);
        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(homework));

        assertThatThrownBy(() -> homeworkService.update(720L,
            new com.njwenglish.dto.homework.HomeworkUpdateRequest(
                "제목", null, null, DUE_AT.minusHours(1))))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        assertThat(homework.getDueAt()).isEqualTo(DUE_AT);
    }

    // ---------- 헬퍼 ----------

    private void givenTeacherAndClassRoom() {
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
    }

    private void givenSavedHomework(Long homeworkId) {
        given(homeworkRepository.save(any(Homework.class))).willAnswer(invocation -> {
            Homework saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", homeworkId);
            return saved;
        });
    }

    private HomeworkCreateRequest request(Long lessonId, Long templateId, boolean saveAsTemplate) {
        return new HomeworkCreateRequest(3L, lessonId, "주간지 전 범위 풀기",
            "워크북 27~35쪽 풀어오기", DUE_AT, templateId, saveAsTemplate);
    }

    private Homework homework(Long id) {
        Homework homework = Homework.create(classRoom, null, teacher, "주간지 전 범위 풀기",
            null, DUE_AT);
        ReflectionTestUtils.setField(homework, "id", id);
        return homework;
    }
}
