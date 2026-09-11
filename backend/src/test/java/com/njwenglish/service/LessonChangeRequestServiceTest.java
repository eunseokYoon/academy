package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.lessonchange.LessonChangeDecideRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonChangeRequest;
import com.njwenglish.entity.Notice;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonChangeRequestRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 수업일 변경. 이 스위트가 고정하는 것은 <b>승인 시 발행되는 공지의 말투</b>다 —
 * 클리닉 변경 공지와 어조가 갈라지면 학생·학부모가 같은 종류의 알림을 두 말투로 받는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonChangeRequestServiceTest {

    @Mock
    private LessonChangeRequestRepository requestRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private NoticeService noticeService;

    private final ClassRoom classRoom =
        Fixtures.openClassRoom(3L, "A고 2학년 목요일반", "HK7F2Q");
    private final Student me = Fixtures.student(88L, "서동환");

    private LessonChangeRequestService service;

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor라 필드 선언 순서가 곧 생성자 순서다
        service = new LessonChangeRequestService(requestRepository, lessonRepository,
            enrollmentRepository, teacherRepository, studentAccessGuard, noticeService);
        given(teacherRepository.findByUserId(any()))
            .willReturn(Optional.of(Fixtures.teacherEntity(1L)));
        // decide()가 발행 결과를 changeRequest에 붙인다. null이면 응답 조립에서 터진다
        given(noticeService.publishForStudent(any(), any(), any(), any()))
            .willReturn(Notice.publishedForStudent("제목", "본문", me,
                Fixtures.teacherEntity(1L), OffsetDateTime.now()));
        Fixtures.login(Fixtures.teacher(1L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("승인 공지는 능동태다 - 클리닉 변경 공지와 같은 형태")
    void 수업일_변경_공지는_능동태다() {
        Lesson from = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 8, 13));
        Lesson to = Fixtures.lesson(502L, classRoom, LocalDate.of(2026, 8, 20));
        LessonChangeRequest request =
            LessonChangeRequest.create(me, from, to, "가족 여행");
        ReflectionTestUtils.setField(request, "id", 30L);
        given(requestRepository.findWithDetail(30L)).willReturn(Optional.of(request));

        service.decide(30L, new LessonChangeDecideRequest(true));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(noticeService).publishForStudent(
            eq("수업일 변경 안내"), body.capture(), eq(me), any());
        assertThat(body.getValue()).startsWith("서동환 학생이 수업일을 변경했습니다.");
        // 수동태로 되돌아가면 여기서 잡힌다
        assertThat(body.getValue()).doesNotContain("변경되었습니다");
        assertThat(body.getValue()).contains("사유 · 가족 여행");
    }

    /** 거절이면 아무에게도 알리지 않는다. 학생만 자기 목록에서 상태를 본다. */
    @Test
    @DisplayName("거절하면 공지를 발행하지 않는다")
    void 거절하면_공지가_없다() {
        Lesson from = Fixtures.lesson(501L, classRoom, LocalDate.of(2026, 8, 13));
        Lesson to = Fixtures.lesson(502L, classRoom, LocalDate.of(2026, 8, 20));
        LessonChangeRequest request =
            LessonChangeRequest.create(me, from, to, "가족 여행");
        ReflectionTestUtils.setField(request, "id", 30L);
        given(requestRepository.findWithDetail(30L)).willReturn(Optional.of(request));

        service.decide(30L, new LessonChangeDecideRequest(false));

        verify(noticeService, org.mockito.Mockito.never())
            .publishForStudent(any(), any(), any(), any());
    }
}
