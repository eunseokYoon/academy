package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.repository.FeedbackRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private TeacherRepository teacherRepository;

    private FeedbackService feedbackService;

    private final Teacher teacher = Fixtures.teacherEntity(1L);
    private ClassRoom classRoom;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        classRoom = ClassRoom.create(teacher, "동성고1 수요일반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        lesson = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 7, 29));
        feedbackService = new FeedbackService(
            feedbackRepository, submissionRepository, teacherRepository);
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GRID 숙제를 확인하면 동그라미가 되고 재제출 표시가 붙는다")
    void checkGridSubmissionResolvesToDone() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
        Submission cell = Fixtures.submission(1L, column, Fixtures.student(88L, "고연준"));
        cell.grade(HomeworkResult.PARTIAL, (short) 50);
        cell.submit(OffsetDateTime.now(), false);

        given(submissionRepository.findById(1L)).willReturn(Optional.of(cell));

        feedbackService.check(1L);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(cell.isResolvedByResubmission()).isTrue();
        assertThat(cell.isChecked()).isTrue();
        assertThat(cell.isResubmitTarget()).isFalse();
    }

    @Test
    @DisplayName("ONLINE 숙제를 확인해도 result는 null로 남는다")
    void checkOnlineSubmissionKeepsResultNull() {
        Homework online = Fixtures.homework(700L, classRoom,
            OffsetDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
        Submission submission = Fixtures.submission(2L, online, Fixtures.student(88L, "고연준"));
        submission.submit(OffsetDateTime.now(), false);

        given(submissionRepository.findById(2L)).willReturn(Optional.of(submission));

        feedbackService.check(2L);

        assertThat(submission.getResult()).isNull();
        assertThat(submission.isResolvedByResubmission()).isFalse();
        assertThat(submission.isChecked()).isTrue();
    }
}
