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
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.LessonVideo;
import com.njwenglish.entity.LessonVideoWatch;
import com.njwenglish.entity.Student;
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

/** 수업 영상 시청 기록(V28, 2026-09-29). 출결은 바꾸지 않는다(2026-09-30). */
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
    private StudentAccessGuard studentAccessGuard;

    private VideoWatchService service;
    private final Student kim = Fixtures.student(91L, "김하늘");
    private final ClassRoom room = Fixtures.openClassRoom(3L, "A반", "ABC123");
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new VideoWatchService(lessonRepository, watchRepository, studentAccessGuard);
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

        /** DB 행이 ON CONFLICT로 만들어졌다고 치고, 잠금 조회가 그 행을 돌려준다. */
        private void firstReport() {
            saved = LessonVideoWatch.start(lesson, kim, URL_A, 10, OffsetDateTime.now());
            given(watchRepository.findForUpdate(30L, 91L, URL_A)).willReturn(Optional.of(saved));
        }

        @Test
        @DisplayName("재생 보고는 본 칸만 칠한다 — 80% 이상이어도 출결은 건드리지 않는다(2026-09-30)")
        void 칸만_칠한다() {
            firstReport();

            service.record(30L, new LessonWatchRequest(EMBED_A, 100.0, range(0, 8)));

            // 출결을 읽거나 바꿀 의존성이 서비스에 아예 없다(생성자). 온라인은 선생님이 고른다
            assertThat(saved.percent()).isEqualTo(80);
        }

        // 2026-09-30 리뷰: 「찾아서 없으면 save」는 첫 보고 둘이 겹치면 유니크 위반 500이 났고,
        // 잠금 없이 읽고 칠해서 늦게 커밋한 보고가 앞 보고의 칸을 덮었다
        @Test
        @DisplayName("행 만들기는 ON CONFLICT, 칠하기는 잠근 행에 한다 — save 경로를 타지 않는다")
        void 만들고_잠가서_칠한다() {
            firstReport();

            service.record(30L, new LessonWatchRequest(EMBED_A, 100.0, range(0, 3)));

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(watchRepository);
            order.verify(watchRepository).insertIfAbsent(30L, 91L, URL_A, 10);
            order.verify(watchRepository).findForUpdate(30L, 91L, URL_A);
            verify(watchRepository, never()).save(any());
            assertThat(saved.percent()).isEqualTo(30);
        }

        @Test
        @DisplayName("이미 칠해진 행에는 칸을 더한다 — 앞 보고의 칸이 지워지지 않는다")
        void 앞_보고의_칸을_지키다() {
            firstReport();
            saved.markWatched(range(0, 4), 10, OffsetDateTime.now()); // 앞 보고가 커밋한 칸

            service.record(30L, new LessonWatchRequest(EMBED_A, 100.0, range(4, 6)));

            assertThat(saved.percent()).isEqualTo(60);
        }

        @Test
        @DisplayName("지금 수업에 없는 영상의 보고는 버린다(수업이 고쳐졌다)")
        void 없는_영상은_버린다() {
            service.record(30L, new LessonWatchRequest(
                "https://www.youtube.com/embed/zzzzzzzzzzz", 100.0, range(0, 10)));
            verify(watchRepository, never()).insertIfAbsent(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt());
        }
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
}
