package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.lesson.LessonWatchRequest;
import com.njwenglish.dto.lesson.VideoWatchState;
import com.njwenglish.entity.Attendance;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonVideo;
import com.njwenglish.entity.LessonVideoWatch;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.LessonVideoWatchRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 수업 영상 시청 기록과 온라인 자동 출결(V28, 2026-09-29). */
@ExtendWith(MockitoExtension.class)
class VideoWatchServiceTest {

    private static final String URL_A = "https://youtu.be/dQw4w9WgXcQ";
    private static final String EMBED_A = "https://www.youtube.com/embed/dQw4w9WgXcQ";
    private static final String URL_B = "https://youtu.be/im83SqpKKJ0";

    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonVideoWatchRepository watchRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private VideoWatchService service;
    private final Student kim = Fixtures.student(91L, "김하늘");
    private final ClassRoom room = Fixtures.openClassRoom(3L, "A반", "ABC123");
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new VideoWatchService(lessonRepository, watchRepository, attendanceRepository,
            studentAccessGuard);
        lesson = Fixtures.lesson(30L, room, LocalDate.of(2026, 9, 21));
    }

    private void videos(String... urls) {
        List<LessonVideo> list = new ArrayList<>();
        for (int i = 0; i < urls.length; i++) {
            list.add(LessonVideo.of(lesson, urls[i], null, (short) i));
        }
        lesson.replaceVideos(list);
    }

    private static List<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().toList();
    }

    private static Attendance attendance(AttendanceStatus status, boolean blocked)
        throws Exception {
        var ctor = Attendance.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Attendance a = ctor.newInstance();
        ReflectionTestUtils.setField(a, "status", status);
        ReflectionTestUtils.setField(a, "onlineAutoBlocked", blocked);
        return a;
    }

    @Nested
    @DisplayName("비트맵")
    class Bitmap {
        @Test
        @DisplayName("지나간 칸만 세고, 같은 칸은 두 번 세지 않고, 범위 밖은 버린다")
        void 칸을_센다() {
            LessonVideoWatch w = LessonVideoWatch.start(lesson, kim, URL_A, 10,
                OffsetDateTime.now());
            w.markWatched(List.of(0, 1, 1, 2, 99, -1), 10, OffsetDateTime.now());
            assertThat(w.percent()).isEqualTo(30);
        }

        @Test
        @DisplayName("길이는 10초 칸으로 올림하고 6시간에서 자른다")
        void 칸_수() {
            assertThat(LessonVideoWatch.bucketsOf(601)).isEqualTo(61);
            assertThat(LessonVideoWatch.bucketsOf(0.5)).isEqualTo(1);
            assertThat(LessonVideoWatch.bucketsOf(99_999)).isEqualTo(2160);
        }

        @Test
        @DisplayName("다른 기기가 더 길게 재면 칸을 늘리고 본 칸은 지킨다")
        void 칸이_늘어난다() {
            LessonVideoWatch w = LessonVideoWatch.start(lesson, kim, URL_A, 8,
                OffsetDateTime.now());
            w.markWatched(range(0, 8), 8, OffsetDateTime.now());
            w.markWatched(List.of(), 16, OffsetDateTime.now());
            assertThat(w.percent()).isEqualTo(50);
        }
    }

    @Test
    @DisplayName("수업 시청률은 영상별 평균이고 안 연 영상은 0%다. 영상이 없으면 null")
    void 수업_시청률() {
        videos(URL_A, URL_B);
        LessonVideoWatch a = LessonVideoWatch.start(lesson, kim, URL_A, 10, OffsetDateTime.now());
        a.markWatched(range(0, 10), 10, OffsetDateTime.now());
        assertThat(VideoWatchService.percentOf(lesson, List.of(a))).isEqualTo(50);

        videos();
        assertThat(VideoWatchService.percentOf(lesson, List.of(a))).isNull();
    }

    @Test
    @DisplayName("학부모 현황은 80% 이상 완료, 조금이라도 보면 일부, 0 이면 미시청")
    void 학부모_현황() {
        assertThat(VideoWatchState.of(80)).isEqualTo(VideoWatchState.WATCHED);
        assertThat(VideoWatchState.of(79)).isEqualTo(VideoWatchState.PARTIAL);
        assertThat(VideoWatchState.of(0)).isEqualTo(VideoWatchState.NOT_WATCHED);
    }

    @Nested
    @DisplayName("재생 보고")
    class Record {
        private LessonVideoWatch saved;

        @BeforeEach
        void setUpLesson() {
            videos(URL_A);
            given(studentAccessGuard.requireSelf()).willReturn(kim);
            given(lessonRepository.findForStudent(30L, 91L)).willReturn(Optional.of(lesson));
        }

        private void firstReport() {
            given(watchRepository.findByLessonIdAndStudentIdAndVideoUrl(30L, 91L, URL_A))
                .willReturn(Optional.empty());
            given(watchRepository.save(any())).willAnswer(i -> {
                saved = i.getArgument(0);
                return saved;
            });
        }

        @Test
        @DisplayName("결석인 학생이 80% 이상 보면 온라인이 된다")
        void 결석이면_온라인() throws Exception {
            firstReport();
            Attendance absent = attendance(AttendanceStatus.ABSENT, false);
            given(watchRepository.findByLessonIdAndStudentId(30L, 91L))
                .willAnswer(i -> List.of(saved));
            given(attendanceRepository.findByLessonIdAndStudentId(30L, 91L))
                .willReturn(Optional.of(absent));

            service.record(30L, new LessonWatchRequest(EMBED_A, 100.0, range(0, 8)));

            assertThat(saved.percent()).isEqualTo(80);
            assertThat(absent.getStatus()).isEqualTo(AttendanceStatus.ONLINE);
        }

        @Test
        @DisplayName("80% 미만이면 결석 그대로다")
        void 미만이면_그대로() throws Exception {
            firstReport();
            given(watchRepository.findByLessonIdAndStudentId(30L, 91L))
                .willAnswer(i -> List.of(saved));

            service.record(30L, new LessonWatchRequest(EMBED_A, 100.0, range(0, 7)));

            verify(attendanceRepository, never()).findByLessonIdAndStudentId(any(), any());
        }

        @Test
        @DisplayName("지금 수업에 없는 영상의 보고는 버린다(수업이 고쳐졌다)")
        void 없는_영상은_버린다() {
            service.record(30L, new LessonWatchRequest(
                "https://www.youtube.com/embed/zzzzzzzzzzz", 100.0, range(0, 10)));
            verify(watchRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("출석한 학생·선생님이 되돌린 학생은 바뀌지 않는다")
    void 출석과_막힘은_그대로() throws Exception {
        Attendance present = attendance(AttendanceStatus.PRESENT, false);
        Attendance blocked = attendance(AttendanceStatus.ABSENT, true);
        assertThat(present.markOnlineByWatch()).isFalse();
        assertThat(blocked.markOnlineByWatch()).isFalse();
        assertThat(present.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(blocked.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    @DisplayName("볼 수 없는 수업(미공개·남의 반)은 404다")
    void 볼_수_없는_수업() {
        given(studentAccessGuard.requireSelf()).willReturn(kim);
        given(lessonRepository.findForStudent(30L, 91L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.record(30L,
            new LessonWatchRequest(EMBED_A, 100.0, List.of(0))))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("선생님이 온라인을 되돌리면 막히고, 온라인으로 찍으면 풀린다")
    void 선생님_수정() throws Exception {
        assertThat(Attendance.nextOnlineAutoBlocked(AttendanceStatus.ONLINE,
            AttendanceStatus.ABSENT, false)).isTrue();
        assertThat(Attendance.nextOnlineAutoBlocked(AttendanceStatus.ABSENT,
            AttendanceStatus.ONLINE, true)).isFalse();
        assertThat(Attendance.nextOnlineAutoBlocked(AttendanceStatus.PRESENT,
            AttendanceStatus.ABSENT, false)).isFalse();

        Attendance online = attendance(AttendanceStatus.ONLINE, false);
        online.correct(AttendanceStatus.ABSENT, null, Fixtures.teacherEntity(1L));
        assertThat(online.isOnlineAutoBlocked()).isTrue();
        assertThat(online.markOnlineByWatch()).isFalse();
    }
}
