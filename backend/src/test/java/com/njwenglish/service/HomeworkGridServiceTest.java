package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.dto.homework.HomeworkGridResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
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
class HomeworkGridServiceTest {

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

    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 7, 29);

    private final Teacher teacher = Fixtures.teacherEntity(1L);
    private ClassRoom classRoom;
    private Lesson lesson;
    private Student goYeonJun;
    private Student kwonTaeHo;

    @BeforeEach
    void setUp() {
        classRoom = ClassRoom.create(teacher, "동성고1 수요일반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        lesson = Fixtures.lesson(501L, classRoom, LESSON_DATE);
        goYeonJun = Fixtures.student(88L, "고연준");
        kwonTaeHo = Fixtures.student(91L, "권태호");

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
    @DisplayName("그리드는 그 수업일 재원생 전원과 열별 칸을 함께 내려준다")
    void grid() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission done = Fixtures.submission(1L, column, goYeonJun);
        done.grade(HomeworkResult.DONE, null);
        Submission ungraded = Fixtures.submission(2L, column, kwonTaeHo);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(done, ungraded));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);

        assertThat(response.lesson().id()).isEqualTo(501L);
        assertThat(response.lesson().lessonDate()).isEqualTo(LESSON_DATE);
        assertThat(response.lesson().classRoomName()).isEqualTo("동성고1 수요일반");
        assertThat(response.students()).extracting(HomeworkGridResponse.StudentInfo::name)
            .containsExactly("고연준", "권태호");
        assertThat(response.columns()).hasSize(1);
        assertThat(response.columns().get(0).title()).isEqualTo("독해 5-8");
        assertThat(response.columns().get(0).cells())
            .extracting(HomeworkGridResponse.CellInfo::result)
            .containsExactly(HomeworkResult.DONE, null);
    }

    @Test
    @DisplayName("재제출을 연 열은 세모·X 학생 수를 대상 수로 센다")
    void resubmitTargetCount() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(LESSON_DATE.plusDays(7).atTime(21, 0).atOffset(java.time.ZoneOffset.ofHours(9)));
        Submission done = Fixtures.submission(1L, column, goYeonJun);
        done.grade(HomeworkResult.DONE, null);
        Submission notDone = Fixtures.submission(2L, column, kwonTaeHo);
        notDone.grade(HomeworkResult.NOT_DONE, null);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(done, notDone));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);

        assertThat(response.columns().get(0).resubmitTargetCount()).isEqualTo(1);
        assertThat(response.columns().get(0).resubmitDueAt()).isNotNull();
    }

    @Test
    @DisplayName("아직 칸이 없는 학생도 명단에 남고 빈 칸으로 나온다")
    void studentWithoutCellStillListed() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission only = Fixtures.submission(1L, column, goYeonJun);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(only));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);

        assertThat(response.columns().get(0).cells())
            .extracting(HomeworkGridResponse.CellInfo::studentId)
            .containsExactly(88L, 91L);
    }
}
