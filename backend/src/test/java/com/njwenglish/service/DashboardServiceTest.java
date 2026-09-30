package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.njwenglish.dto.home.TeacherDashboardResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.enums.ClassRoomStatus;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import com.njwenglish.entity.enums.StudentStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import com.njwenglish.entity.Teacher;
import com.njwenglish.repository.QnaPostRepository;
import com.njwenglish.repository.TeacherRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * todo가 T-1의 존재 이유다. <b>뒤의 두 항목(recentSignupCount·openJoinCodeCount)이
 * 반 코드 가입의 안전장치</b>라서 숫자가 틀리면 제3자 가입을 못 잡는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private QnaPostRepository qnaPostRepository;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(lessonRepository, studentRepository,
            classRoomRepository, enrollmentRepository, teacherRepository, qnaPostRepository);
        given(lessonRepository.findByDateWithClassRoom(any())).willReturn(List.of());
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("새 질문은 선생님이 게시판을 마지막으로 연 뒤의 질문 수다")
    void 새_질문은_마지막으로_본_뒤를_센다() {
        Teacher teacher = Fixtures.teacherEntity(1L);
        OffsetDateTime seenAt = OffsetDateTime.parse("2026-09-28T10:00:00+09:00");
        teacher.markQnaSeen(seenAt);
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(qnaPostRepository.countRootsCreatedAfter(seenAt)).willReturn(3L);

        assertThat(dashboardService.dashboard().todo().newQuestionCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("게시판을 열면 시각이 바뀌고, 앞의 시각을 돌려준다")
    void 게시판을_열면_시각이_바뀐다() {
        Teacher teacher = Fixtures.teacherEntity(1L);
        OffsetDateTime before = OffsetDateTime.parse("2026-09-28T10:00:00+09:00");
        teacher.markQnaSeen(before);
        OffsetDateTime now = OffsetDateTime.parse("2026-09-29T09:00:00+09:00");

        assertThat(teacher.markQnaSeen(now)).isEqualTo(before);
        assertThat(teacher.getQnaSeenAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("todo 6개가 각자 자기 조건의 count와 이어져 있다")
    void todo_항목이_실제_집계와_이어진다() {
        given(lessonRepository.countPendingUntil(any())).willReturn(3L);
        given(lessonRepository.countUnwrittenUntil(any())).willReturn(2L);
        given(studentRepository.countByStatusAndUserIsNull(StudentStatus.ENROLLED)).willReturn(12L);
        given(studentRepository.countByStatusAndParentIsNull(StudentStatus.ENROLLED)).willReturn(7L);
        given(studentRepository.countByCreatedAtGreaterThanEqual(any())).willReturn(4L);
        given(classRoomRepository.countByStatusAndJoinCodeActiveTrue(ClassRoomStatus.ACTIVE))
            .willReturn(2L);
        given(studentRepository.countByStatus(StudentStatus.ENROLLED)).willReturn(197L);
        given(classRoomRepository.countByStatus(ClassRoomStatus.ACTIVE)).willReturn(9L);

        TeacherDashboardResponse.Todo todo = dashboardService.dashboard().todo();

        assertThat(todo.pendingAttendanceCount()).isEqualTo(3);
        assertThat(todo.unwrittenLessonCount()).isEqualTo(2);
        assertThat(todo.unsignedStudentCount()).isEqualTo(12);
        assertThat(todo.unlinkedParentCount()).isEqualTo(7);
        assertThat(todo.recentSignupCount()).isEqualTo(4);
        assertThat(todo.openJoinCodeCount()).isEqualTo(2);

        assertThat(dashboardService.dashboard().stats().totalStudents()).isEqualTo(197);
        assertThat(dashboardService.dashboard().stats().activeClassRooms()).isEqualTo(9);
    }

    @Test
    @DisplayName("신규 가입 점검 기간은 7일이다")
    void 신규_가입은_최근_7일을_센다() {
        dashboardService.dashboard();

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        org.mockito.Mockito.verify(studentRepository)
            .countByCreatedAtGreaterThanEqual(from.capture());

        assertThat(from.getValue()).isCloseTo(OffsetDateTime.now().minusDays(7),
            org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("할 일이 없어도 todo는 전부 0으로 채워진다 — 필드를 빼지 않는다")
    void 할_일이_없으면_전부_0이다() {
        TeacherDashboardResponse.Todo todo = dashboardService.dashboard().todo();

        // 0이어도 화면에서 recentSignupCount·openJoinCodeCount는 숨기지 않는다
        assertThat(todo.recentSignupCount()).isZero();
        assertThat(todo.openJoinCodeCount()).isZero();
    }

    @Test
    @DisplayName("오늘 수업의 시작 시각은 반에서 오고, 내용 작성 여부가 함께 나간다")
    void 오늘_수업은_반_시각과_작성_여부를_보여준다() {
        Lesson lesson = Fixtures.lesson(501L, classRoom, LocalDate.now());
        given(lessonRepository.findByDateWithClassRoom(any())).willReturn(List.of(lesson));
        given(enrollmentRepository.countActiveStudentsOn(any(), any())).willReturn(20L);

        TeacherDashboardResponse.Today today = dashboardService.dashboard().today();

        assertThat(today.lessons()).hasSize(1);
        assertThat(today.lessons().get(0).studentCount()).isEqualTo(20);
        assertThat(today.lessons().get(0).contentWritten()).isFalse();
        assertThat(today.lessons().get(0).attendanceStatus())
            .isEqualTo(LessonAttendanceStatus.PENDING);
    }
}
