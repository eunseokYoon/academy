package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.lesson.LessonReportResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LessonReportServiceTest {

    @Mock
    private LessonRepository lessonRepository;
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
    @Mock
    private VideoWatchService videoWatchService;

    private LessonReportService service;

    private final Student kim = Fixtures.student(91L, "김하늘");
    private final ClassRoom room = Fixtures.openClassRoom(1L, "A반", "ABC123");
    private final Lesson lesson = Fixtures.lesson(30L, room, LocalDate.of(2026, 9, 21));

    @BeforeEach
    void setUp() {
        service = new LessonReportService(lessonRepository, homeworkRepository,
            submissionRepository, attendanceRepository, enrollmentRepository, studentAccessGuard,
            videoWatchService);
    }

    @Test
    @DisplayName("학부모 수업 상세는 그 수업의 숙제를 전부 내려준다 — 첫 숙제도 그대로 남긴다")
    void 숙제를_전부_내려준다() {
        // 한 수업에 그리드 열이 둘이다. 하나만 붙이던 때 주간 레포트가 숙제를 하나만 보였다
        Homework words = Fixtures.gridColumn(501L, room, lesson, "단어 3과", (short) 1);
        Homework workbook = Fixtures.gridColumn(502L, room, lesson, "워크북 12쪽", (short) 2);
        Submission done = Fixtures.submission(9001L, words, kim);
        done.grade(HomeworkResult.DONE, null);
        given(studentAccessGuard.requireAccessible(91L)).willReturn(kim);
        given(lessonRepository.findForStudent(30L, 91L)).willReturn(Optional.of(lesson));
        given(homeworkRepository.findByLessonIds(List.of(30L))).willReturn(List.of(words, workbook));
        given(submissionRepository.findByHomeworkAndStudent(501L, 91L)).willReturn(Optional.of(done));
        given(submissionRepository.findByHomeworkAndStudent(502L, 91L)).willReturn(Optional.empty());
        given(attendanceRepository.findByLessonIdAndStudentId(30L, 91L)).willReturn(Optional.empty());

        LessonReportResponse response = service.childLesson(91L, 30L);

        assertThat(response.homeworks()).extracting(LessonReportResponse.Homework::title)
            .containsExactly("단어 3과", "워크북 12쪽");
        assertThat(response.homeworks().get(0).result()).isEqualTo(HomeworkResult.DONE);
        // 옛 화면이 읽는 필드는 첫 숙제로 남는다(7-3)
        assertThat(response.homework()).isEqualTo(response.homeworks().get(0));
        // 학부모에게 숙제 내용은 가지 않는다(4-8)
        assertThat(response.homeworks()).allMatch(h -> h.description() == null);
        assertThat(response.videos()).isEmpty();
    }

    @Test
    @DisplayName("숙제가 없으면 빈 배열이고 첫 숙제는 null 이다")
    void 숙제가_없으면_빈_배열() {
        given(studentAccessGuard.requireAccessible(91L)).willReturn(kim);
        given(lessonRepository.findForStudent(30L, 91L)).willReturn(Optional.of(lesson));
        given(homeworkRepository.findByLessonIds(List.of(30L))).willReturn(List.of());
        given(attendanceRepository.findByLessonIdAndStudentId(30L, 91L)).willReturn(Optional.empty());

        LessonReportResponse response = service.childLesson(91L, 30L);

        assertThat(response.homeworks()).isEmpty();
        assertThat(response.homework()).isNull();
    }
}
