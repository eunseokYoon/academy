package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.dto.lesson.LessonBulkCreateRequest;
import com.njwenglish.dto.lesson.LessonBulkCreateResponse;
import com.njwenglish.dto.lesson.LessonCreateRequest;
import com.njwenglish.dto.lesson.LessonDetailResponse;
import com.njwenglish.dto.lesson.LessonUpdateRequest;
import com.njwenglish.dto.lesson.LessonVideoRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;

    private LessonService lessonService;

    /** 목요일(4) 반. bulk 생성 기준이 된다. */
    private ClassRoom thursdayClass() {
        ClassRoom classRoom = ClassRoom.create(null, "고2 심화반", "HK7F2Q", null);
        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 4, LocalTime.of(19, 0), null)));
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        return classRoom;
    }

    /** 화(2)·목(4) 주 2회 반. */
    private ClassRoom tuesdayThursdayClass() {
        ClassRoom classRoom = ClassRoom.create(null, "화목반", "KD4REX", null);
        classRoom.replaceSchedules(List.of(
            new ClassRoom.Slot((short) 2, LocalTime.of(19, 0), LocalTime.of(21, 0)),
            new ClassRoom.Slot((short) 4, LocalTime.of(19, 0), LocalTime.of(21, 0))));
        ReflectionTestUtils.setField(classRoom, "id", 4L);
        return classRoom;
    }

    @BeforeEach
    void setUp() {
        lessonService = new LessonService(lessonRepository, classRoomRepository, enrollmentRepository, eventPublisher);
    }

    @Test
    @DisplayName("선생님이 고른 주차를 그대로 저장한다 — 날짜에서 계산해 덮어쓰지 않는다")
    void 주차를_그대로_저장한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        LessonDetailResponse response = lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 4,
            "관계대명사", null, "내용", "중점", "다음", null));

        // 5월 20일은 달력상 3주차지만 선생님이 고른 4를 저장한다
        assertThat(response.week()).isEqualTo((short) 4);
        assertThat(response.attendanceStatus()).isEqualTo(LessonAttendanceStatus.PENDING);
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    @DisplayName("같은 반 같은 날짜는 409다")
    void 중복_날짜는_거부한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.existsByClassRoomIdAndLessonDate(3L, LocalDate.of(2026, 5, 20)))
            .willReturn(true);

        assertThatThrownBy(() -> lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 4,
            null, null, null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("YouTube가 아닌 주소는 400이다")
    void 잘못된_영상주소는_거부한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));

        assertThatThrownBy(() -> lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 4,
            null, List.of(new LessonVideoRequest("https://vimeo.com/12345678", null)),
            null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("링크 목록에서 embedUrl을 파싱해 내려준다 — 순서는 보낸 순서다")
    void 영상_id를_내려준다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        LessonDetailResponse response = lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 4,
            null, List.of(
                new LessonVideoRequest("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "1교시"),
                new LessonVideoRequest("https://youtu.be/im83SqpKKJ0", null)),
            null, null, null, null));

        assertThat(response.videos()).hasSize(2);
        assertThat(response.videos().get(0).title()).isEqualTo("1교시");
        assertThat(response.videos().get(0).embedUrl())
            .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");
        // 이름을 안 적으면 null이다. "영상 2"는 화면이 채운다
        assertThat(response.videos().get(1).title()).isNull();
        assertThat(response.videos().get(1).embedUrl())
            .isEqualTo("https://www.youtube.com/embed/im83SqpKKJ0");
    }

    @Test
    @DisplayName("주차는 1~5만 받는다 (DB CHECK와 같은 범위)")
    void 주차_범위를_검사한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));

        assertThatThrownBy(() -> lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 6,
            null, null, null, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("bulk는 반의 요일에 해당하는 날만 만들고 skipDates와 기존 날짜를 건너뛴다")
    void 요일_기준으로_일괄_생성한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.findLessonDates(3L, LocalDate.of(2026, 8, 6),
            LocalDate.of(2026, 9, 3))).willReturn(List.of(LocalDate.of(2026, 8, 13)));
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        LessonBulkCreateResponse response = lessonService.bulkCreate(new LessonBulkCreateRequest(
            3L, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 9, 3),
            List.of(LocalDate.of(2026, 8, 20))));

        // 8/6 8/13 8/20 8/27 9/3 중 8/13(기존)·8/20(skip)을 뺀 3일
        assertThat(response.created()).isEqualTo(3);
        assertThat(response.skipped()).isEqualTo(2);
        assertThat(response.createdDates()).containsExactly(
            LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("bulk가 채우는 주차는 달력 기준 1~5다")
    void 기본_주차를_채운다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        lessonService.bulkCreate(new LessonBulkCreateRequest(
            3L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null));

        ArgumentCaptor<Lesson> saved = ArgumentCaptor.forClass(Lesson.class);
        verify(lessonRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues())
            .extracting(Lesson::getLessonDate, Lesson::getWeek)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 6), (short) 1),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 13), (short) 2),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 20), (short) 3),
                org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 27), (short) 4));
    }

    @Test
    @DisplayName("반에 요일이 없으면 bulk를 쓸 수 없다")
    void 요일이_없으면_거부한다() {
        given(classRoomRepository.findById(3L))
            .willReturn(Optional.of(Fixtures.openClassRoom(3L, "특강", "HK7F2Q")));

        assertThatThrownBy(() -> lessonService.bulkCreate(new LessonBulkCreateRequest(
            3L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("PATCH는 published_at을 건드리지 않는다")
    void 수정은_공개상태를_바꾸지_않는다() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));

        lessonService.publish(501L);
        var publishedAt = lesson.getPublishedAt();
        lessonService.update(501L, new LessonUpdateRequest(
            null, null, null, "제목 수정", null, null, null, null, null));

        assertThat(lesson.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(lesson.getTitle()).isEqualTo("제목 수정");
    }

    @Test
    @DisplayName("이미 공개된 수업을 다시 공개해도 시각은 그대로다")
    void 공개는_한번만_기록한다() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));

        lessonService.publish(501L);
        var first = lesson.getPublishedAt();
        lessonService.publish(501L);

        assertThat(lesson.getPublishedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("PATCH는 보낸 필드만 바꾼다")
    void 보낸_필드만_바꾼다() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        lesson.writeContent("원래 제목", "원래 내용", "원래 중점", "원래 숙제", "원래 클리닉");
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));

        lessonService.update(501L, new LessonUpdateRequest(
            null, null, (short) 3, null, null, null, null, null, "다음 시간 클리닉"));

        assertThat(lesson.getWeek()).isEqualTo((short) 3);
        assertThat(lesson.getYear()).isEqualTo((short) 2026);
        assertThat(lesson.getTitle()).isEqualTo("원래 제목");
        assertThat(lesson.getContent()).isEqualTo("원래 내용");
        assertThat(lesson.getHomeworkNote()).isEqualTo("원래 숙제");
        assertThat(lesson.getClinicNote()).isEqualTo("다음 시간 클리닉");
    }

    @Test
    @DisplayName("수업 숙제와 클리닉을 따로 저장한다")
    void 숙제와_클리닉을_따로_저장한다() {
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(thursdayClass()));
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        LessonDetailResponse response = lessonService.create(new LessonCreateRequest(
            3L, LocalDate.of(2026, 5, 20), (short) 2026, (short) 5, (short) 4,
            "관계대명사", null, "내용", "중점",
            "p.144-152 (1,2번 제외)", "화 19:00 도착"));

        // 한 칸에 섞여 있던 것을 나눈 것이 이 태스크의 전부다. 섞이면 학생이 골라 읽어야 한다
        assertThat(response.homeworkNote()).isEqualTo("p.144-152 (1,2번 제외)");
        assertThat(response.clinicNote()).isEqualTo("화 19:00 도착");
    }

    /** 부분 수정이다. null은 "안 바꾼다"다 — 빈 문자열을 보내면 지워진다. */
    @Test
    @DisplayName("수정에서 클리닉만 보내면 숙제는 그대로 남는다")
    void 한_칸만_수정하면_나머지는_남는다() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        lesson.writeContent("관계대명사", "내용", "중점", "먼저 적어 둔 숙제", "옛 클리닉");
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));

        lessonService.update(501L, new LessonUpdateRequest(
            null, null, null, null, null, null, null, null, "바뀐 클리닉 안내"));

        assertThat(lesson.getHomeworkNote()).isEqualTo("먼저 적어 둔 숙제");
        assertThat(lesson.getClinicNote()).isEqualTo("바뀐 클리닉 안내");
    }

    @Test
    @DisplayName("출석·숙제·시청 기록이 걸린 수업은 삭제하지 않고 409다")
    void 기록이_있으면_삭제를_거부한다() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));
        given(lessonRepository.hasRecords(501L)).willReturn(true);

        assertThatThrownBy(() -> lessonService.delete(501L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.LESSON_HAS_RECORDS);

        verify(lessonRepository, never()).delete(any());
    }

    @Test
    @DisplayName("슬롯이 여러 개면 모든 요일에 수업을 만든다")
    void 여러_요일에_일괄_생성한다() {
        given(classRoomRepository.findById(4L)).willReturn(Optional.of(tuesdayThursdayClass()));
        given(lessonRepository.findLessonDates(4L, LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 16))).willReturn(List.of());
        given(lessonRepository.save(any())).willAnswer(i -> i.getArgument(0));

        LessonBulkCreateResponse response = lessonService.bulkCreate(new LessonBulkCreateRequest(
            4L, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 16), null));

        // 화 8/4 8/11, 목 8/6 8/13 → 4일
        assertThat(response.created()).isEqualTo(4);
    }

    @Test
    @DisplayName("슬롯이 없는 반은 일괄 생성을 거부한다")
    void 슬롯_없으면_거부한다() {
        ClassRoom noSchedule = ClassRoom.create(null, "미정반", "AAAAAA", null);
        ReflectionTestUtils.setField(noSchedule, "id", 5L);
        given(classRoomRepository.findById(5L)).willReturn(Optional.of(noSchedule));

        assertThatThrownBy(() -> lessonService.bulkCreate(new LessonBulkCreateRequest(
            5L, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 16), null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(lessonRepository, never()).save(any());
    }

    @Test
    @DisplayName("수업 공개 푸시(#9)는 처음 공개할 때 그 수업일의 재원생에게 한 번만 나간다")
    void 수업_공개_푸시는_한번만() {
        Lesson lesson = Lesson.create(thursdayClass(), LocalDate.of(2026, 5, 20),
            (short) 2026, (short) 5, (short) 4);
        ReflectionTestUtils.setField(lesson, "id", 501L);
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(any(), eq(LocalDate.of(2026, 5, 20))))
            .willReturn(List.of(Fixtures.student(88L, "서동환")));

        lessonService.publish(501L);
        lessonService.publish(501L);

        verify(eventPublisher, times(1)).publishEvent(
            PushEvent.of(PushTopic.LESSON_PUBLISHED, List.of(88L), 501L));
    }
}
