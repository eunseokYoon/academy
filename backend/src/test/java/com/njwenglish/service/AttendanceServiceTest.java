package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.attendance.AttendanceCalendarResponse;
import com.njwenglish.dto.attendance.AttendanceConfirmRequest;
import com.njwenglish.dto.attendance.AttendanceConfirmResponse;
import com.njwenglish.dto.attendance.AttendanceExceptionRequest;
import com.njwenglish.dto.attendance.AttendanceRosterResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.AttendanceStatus;
import com.njwenglish.entity.enums.LessonAttendanceStatus;
import com.njwenglish.repository.AttendanceRepository.CalendarRow;
import com.njwenglish.repository.AttendanceRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private AttendanceService attendanceService;

    private static final LocalDate LESSON_DATE = LocalDate.of(2026, 5, 20);

    private final Student seo = Fixtures.student(88L, "서동환");
    private final Student kim = Fixtures.student(91L, "김하늘");
    private final Student park = Fixtures.student(97L, "박서준");
    private final Teacher teacher = Fixtures.teacherEntity(1L);

    private Lesson lesson;

    @Mock
    private VideoWatchService videoWatchService;

    @BeforeEach
    void setUp() {
        ClassRoom classRoom = ClassRoom.create(null, "고2 심화반", "HK7F2Q", null);
        ReflectionTestUtils.setField(classRoom, "id", 3L);
        lesson = Fixtures.lesson(501L, classRoom, LESSON_DATE);

        attendanceService = new AttendanceService(attendanceRepository, lessonRepository,
            enrollmentRepository, teacherRepository, submissionRepository, studentAccessGuard, eventPublisher,
            videoWatchService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsTeacher() {
        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
    }

    private void givenLessonWithRoster(Student... students) {
        given(lessonRepository.findWithClassRoom(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(students));
    }

    @Test
    @DisplayName("확정 전 명단은 전원 PRESENT로 초기화된다 — 선생님은 안 온 학생만 지정한다")
    void 확정_전_명단은_전원_출석이다() {
        givenLessonWithRoster(seo, kim, park);

        AttendanceRosterResponse roster = attendanceService.roster(501L);

        assertThat(roster.attendanceStatus()).isEqualTo(LessonAttendanceStatus.PENDING);
        assertThat(roster.students()).hasSize(3)
            .allMatch(row -> row.status() == AttendanceStatus.PRESENT && row.memo() == null);
        verify(attendanceRepository, never()).findByLessonId(anyLong());
    }

    @Test
    @DisplayName("명단은 오늘이 아니라 수업일 기준으로 뽑는다 — 5월 수업을 6월에 입력해도 5월 재원생이 나온다")
    void 명단은_수업일_기준_재원생이다() {
        givenLessonWithRoster(seo, kim);

        attendanceService.roster(501L);

        // 오늘(LocalDate.now())이 아니라 lesson_date로 조회해야 한다.
        // 오늘 기준이면 수업일 이후 퇴원한 학생이 그 수업 명단에서 빠진다
        verify(enrollmentRepository).findActiveStudents(3L, LESSON_DATE);
        verify(enrollmentRepository, never()).findActiveStudents(3L, LocalDate.now());
    }

    @Test
    @DisplayName("예외가 빈 배열이면 전원 출석으로 확정된다")
    void 예외가_빈_배열이면_전원_출석으로_확정된다() {
        loginAsTeacher();
        givenLessonWithRoster(seo, kim, park);

        AttendanceConfirmResponse response =
            attendanceService.confirm(501L, new AttendanceConfirmRequest(List.of(), null));

        assertThat(response.attendanceStatus()).isEqualTo(LessonAttendanceStatus.CONFIRMED);
        assertThat(response.summary().present()).isEqualTo(3);
        assertThat(response.summary().absent()).isZero();
        verify(attendanceRepository, times(3)).upsert(eq(501L), eq(3L), anyLong(),
            eq(LESSON_DATE), eq("PRESENT"), eq(null), eq(1L), eq(false));
        assertThat(lesson.getAttendanceStatus()).isEqualTo(LessonAttendanceStatus.CONFIRMED);
        assertThat(lesson.getAttendanceConfirmedBy()).isSameAs(teacher);
    }

    @Test
    @DisplayName("예외 2명만 담아도 재원생 전원의 행이 생기고 attend_date가 수업일과 같다")
    void 예외만_보내도_전원_행이_생긴다() {
        loginAsTeacher();
        givenLessonWithRoster(seo, kim, park);

        AttendanceConfirmResponse response = attendanceService.confirm(501L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, "무단"),
                new AttendanceExceptionRequest(97L, AttendanceStatus.SICK, "병원 진료")), null));

        assertThat(response.summary().present()).isEqualTo(1);
        assertThat(response.summary().absent()).isEqualTo(1);
        assertThat(response.summary().sick()).isEqualTo(1);

        // attend_date에 lesson_date를 복사하지 않으면 캘린더 조회가 통째로 빈다
        verify(attendanceRepository).upsert(501L, 3L, 88L, LESSON_DATE, "PRESENT", null, 1L, false);
        verify(attendanceRepository).upsert(501L, 3L, 91L, LESSON_DATE, "ABSENT", "무단", 1L, false);
        // 푸시 #8 — 결석·병결도 포함한 재원생 전원. 하루 1건 묶기는 PushPlanner 몫이다
        verify(eventPublisher).publishEvent(PushEvent.of(PushTopic.ATTENDANCE_LESSON,
            List.of(seo.getId(), kim.getId(), park.getId()), null));
        verify(attendanceRepository).upsert(501L, 3L, 97L, LESSON_DATE, "SICK", "병원 진료", 1L, false);
    }

    @Test
    @DisplayName("confirm을 두 번 호출해도 409가 아니라 재확정으로 동작한다")
    void confirm을_두_번_호출해도_행이_중복되지_않는다() {
        loginAsTeacher();
        givenLessonWithRoster(seo, kim);

        attendanceService.confirm(501L, new AttendanceConfirmRequest(List.of(), null));
        AttendanceConfirmResponse second = attendanceService.confirm(501L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(91L, AttendanceStatus.LATE, null)), null));

        // 중복 방지는 upsert의 ON CONFLICT (student_id, lesson_id)가 맡는다.
        // 서비스는 두 번째 호출을 정상 정정 흐름으로 받아들여야 한다
        assertThat(second.attendanceStatus()).isEqualTo(LessonAttendanceStatus.CONFIRMED);
        assertThat(second.summary().late()).isEqualTo(1);
        verify(attendanceRepository, times(2)).upsert(eq(501L), eq(3L), eq(91L),
            eq(LESSON_DATE), any(), any(), eq(1L), eq(false));
    }

    @Test
    @DisplayName("명단에 없는 studentId를 예외로 보내면 400이다")
    void 명단에_없는_학생이_섞이면_400이다() {
        // 검증이 currentTeacher()보다 먼저다. 로그인 스텁이 필요 없다는 게 그 증거다
        givenLessonWithRoster(seo, kim);

        assertThatThrownBy(() -> attendanceService.confirm(501L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(999L, AttendanceStatus.ABSENT, null)), null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(attendanceRepository, never()).upsert(anyLong(), anyLong(), anyLong(),
            any(), any(), any(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("미확정 수업일은 캘린더에서 PENDING으로 내려온다")
    void 미확정_수업일은_캘린더에서_PENDING으로_내려온다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 20),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.ABSENT),
                row(502L, LocalDate.of(2026, 5, 27),
                    LessonAttendanceStatus.PENDING, null)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.days()).hasSize(2);
        assertThat(calendar.days().get(1).status()).isEqualTo("PENDING");
        // 그날 숙제가 없으면 null이다. 0이면 학부모 캘린더에 빨간 띠가 뜬다
        assertThat(calendar.days()).allMatch(day -> day.homeworkRate() == null);
        assertThat(calendar.homeworkCompletionRate()).isNull();
    }

    @Test
    @DisplayName("숙제가 없던 수업일은 월 합계의 분모에 들어가지 않는다")
    void 숙제가_없는_날은_월_합계_분모에서_빠진다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(anyLong(), any(), any()))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT),
                row(502L, LocalDate.of(2026, 5, 13),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        // 5/6에만 숙제가 있다. 5문항 중 3개 제출 → scoreSum 300(=3*100), targetCount 5
        given(submissionRepository.findHomeworkRates(88L, List.of(501L, 502L)))
            .willReturn(List.of(rate(501L, 300, 5)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        // 5/13은 숙제가 없었다. 분모에 들어가면 월 합계가 30으로 반토막 난다
        assertThat(calendar.homeworkCompletionRate()).isEqualTo(60);
    }

    /**
     * 날짜 칸의 띠는 2026-09-10에 없앴다. 값을 지우지 않고 null을 내리는 이유는
     * 배포 순서가 backend → web이라, 필드를 빼면 옛 화면에서 undefined !== null이
     * 참이 되어 NaN% 그라디언트가 그려지기 때문이다.
     */
    @Test
    @DisplayName("숙제가 있어도 날짜 칸의 완료율은 null이다 - 띠를 없앴다")
    void 날짜_칸_완료율은_항상_null이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        given(submissionRepository.findHomeworkRates(88L, List.of(501L)))
            .willReturn(List.of(rate(501L, 300, 5)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.days()).hasSize(1);
        assertThat(calendar.days().get(0).homeworkRate()).isNull();
        // 계산은 살아 있다. 카드가 이 값을 쓴다
        assertThat(calendar.homeworkCompletionRate()).isEqualTo(60);
    }

    /** ⭕=100, 🔺=퍼센트, ❌=0의 합이 scoreSum이다. done*100이 아니다. */
    private SubmissionRepository.HomeworkRateRow rate(Long lessonId, long scoreSum, long targetCount) {
        return new SubmissionRepository.HomeworkRateRow() {
            @Override
            public Long getLessonId() {
                return lessonId;
            }

            @Override
            public long getScoreSum() {
                return scoreSum;
            }

            @Override
            public long getTargetCount() {
                return targetCount;
            }
        };
    }

    @Test
    @DisplayName("동그라미만 있는 수업일의 숙제율은 100이다 - 온라인 제출이 없어도")
    void 동그라미는_온라인_제출이_없어도_숙제율_100이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        // ⭕ 2칸 = 100 + 100
        given(submissionRepository.findHomeworkRates(88L, List.of(501L)))
            .willReturn(List.of(rate(501L, 200, 2)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.homeworkCompletionRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("세모 50과 동그라미가 섞이면 숙제율은 75다")
    void 세모50과_동그라미가_섞이면_숙제율은_75다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        // ⭕(100) + 🔺50(50) = 150, 대상 2칸
        given(submissionRepository.findHomeworkRates(88L, List.of(501L)))
            .willReturn(List.of(rate(501L, 150, 2)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.homeworkCompletionRate()).isEqualTo(75);
    }

    @Test
    @DisplayName("숙제율은 버림이 아니라 반올림이다 - 200/3은 66이 아니라 67이다")
    void 숙제율은_버림이_아니라_반올림이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        // 200/3 = 66.67 - 정수 나눗셈(버림)이면 66, 반올림이면 67이다
        given(submissionRepository.findHomeworkRates(88L, List.of(501L)))
            .willReturn(List.of(rate(501L, 200, 3)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.homeworkCompletionRate()).isEqualTo(67);
    }

    @Test
    @DisplayName("미채점만 있는 수업일의 숙제율은 0이 아니라 null이다")
    void 미채점만_있는_수업일의_숙제율은_null이다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(88L,
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT)));
        // 미채점 칸은 쿼리에서 제외되므로 그 lessonId 행 자체가 안 나온다
        given(submissionRepository.findHomeworkRates(88L, List.of(501L)))
            .willReturn(List.of());

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.homeworkCompletionRate()).isNull();
    }

    @Test
    @DisplayName("미확정 날짜는 출석 집계에 포함되지 않는다")
    void 미확정_날짜는_출석_집계에_포함되지_않는다() {
        given(studentAccessGuard.requireAccessible(88L)).willReturn(seo);
        given(attendanceRepository.findCalendarRows(anyLong(), any(), any()))
            .willReturn(List.of(
                row(501L, LocalDate.of(2026, 5, 6),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.PRESENT),
                row(502L, LocalDate.of(2026, 5, 13),
                    LessonAttendanceStatus.CONFIRMED, AttendanceStatus.LATE),
                // 미래 수업일도 PENDING이다. 집계에 들어가면 아직 오지 않은 날이 출석이 된다
                row(503L, LocalDate.of(2026, 5, 20), LessonAttendanceStatus.PENDING, null),
                row(504L, LocalDate.of(2026, 5, 27), LessonAttendanceStatus.PENDING, null)));

        AttendanceCalendarResponse calendar = attendanceService.calendar(88L, 2026, 5);

        assertThat(calendar.days()).hasSize(4);
        assertThat(calendar.summary().present()).isEqualTo(1);
        assertThat(calendar.summary().late()).isEqualTo(1);
        assertThat(calendar.summary().absent()).isZero();
    }

    @Test
    @DisplayName("캘린더 조회는 첫 줄이 권한 검증이다 — 남의 자녀면 403")
    void 남의_자녀_캘린더는_막힌다() {
        given(studentAccessGuard.requireAccessible(88L))
            .willThrow(new BusinessException(ErrorCode.STUDENT_NOT_ACCESSIBLE));

        assertThatThrownBy(() -> attendanceService.calendar(88L, 2026, 5))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDENT_NOT_ACCESSIBLE);

        verify(attendanceRepository, never()).findCalendarRows(anyLong(), any(), any());
    }

    @Test
    @DisplayName("미확정 목록은 오늘까지로 자른다 — 미래 수업은 아직 확정할 수 없다")
    void 미확정_목록은_미래_수업을_제외한다() {
        given(lessonRepository.findPendingUntil(LocalDate.now())).willReturn(List.of(lesson));
        given(enrollmentRepository.countActiveStudentsOn(3L, LESSON_DATE)).willReturn(20L);

        var pending = attendanceService.pending();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).lessonId()).isEqualTo(501L);
        assertThat(pending.get(0).studentCount()).isEqualTo(20L);
    }

    private CalendarRow row(Long lessonId, LocalDate date,
                            LessonAttendanceStatus lessonStatus, AttendanceStatus attendStatus) {
        return new CalendarRow() {
            @Override
            public Long getLessonId() {
                return lessonId;
            }

            @Override
            public LocalDate getLessonDate() {
                return date;
            }

            @Override
            public LessonAttendanceStatus getLessonStatus() {
                return lessonStatus;
            }

            @Override
            public AttendanceStatus getAttendStatus() {
                return attendStatus;
            }
        };
    }

    // ---------- 온라인(2026-09-29) ----------

    private static com.njwenglish.entity.Attendance savedRow(Student student,
                                                            AttendanceStatus status,
                                                            boolean blocked) throws Exception {
        var ctor = com.njwenglish.entity.Attendance.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var a = ctor.newInstance();
        ReflectionTestUtils.setField(a, "student", student);
        ReflectionTestUtils.setField(a, "status", status);
        ReflectionTestUtils.setField(a, "onlineAutoBlocked", blocked);
        return a;
    }

    @Test
    @DisplayName("결석으로 확정해도 이미 영상을 80% 이상 봤으면 온라인이다")
    void 결석인데_영상을_봤으면_온라인() {
        loginAsTeacher();
        givenLessonWithRoster(seo, kim);
        given(videoWatchService.lessonPercents(lesson)).willReturn(java.util.Map.of(91L, 85));

        AttendanceConfirmResponse response = attendanceService.confirm(501L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, null)), null));

        verify(attendanceRepository).upsert(501L, 3L, 91L, LESSON_DATE, "ONLINE", null, 1L, false);
        // 온라인은 출석 쪽이다 — 결석으로 세지 않는다
        assertThat(response.summary().absent()).isZero();
        assertThat(response.summary().online()).isEqualTo(1);
    }

    @Test
    @DisplayName("선생님이 온라인을 결석으로 되돌려 재확정하면 결석이고, 막혀서 다시 안 바뀐다")
    void 선생님이_되돌리면_막힌다() throws Exception {
        loginAsTeacher();
        givenLessonWithRoster(kim);
        given(attendanceRepository.findByLessonId(501L))
            .willReturn(List.of(savedRow(kim, AttendanceStatus.ONLINE, false)));
        given(videoWatchService.lessonPercents(lesson)).willReturn(java.util.Map.of(91L, 95));

        attendanceService.confirm(501L, new AttendanceConfirmRequest(List.of(
            new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, "안 봄")), null));

        verify(attendanceRepository).upsert(501L, 3L, 91L, LESSON_DATE, "ABSENT", "안 봄", 1L,
            true);
    }

    // 2026-09-30 리뷰: 선생님이 결석으로 확정하고 명단을 열어 둔 사이 학생이 영상을 봐
    // 온라인이 됐다. 같은 화면에서 다른 학생을 고쳐 재확정하면 화면의 옛 값(결석)이 올라가
    // 「선생님이 되돌림」으로 읽혔고, 그 학생은 영구히 결석으로 막혔다.
    @Test
    @DisplayName("명단을 연 뒤에 영상 시청으로 온라인이 된 학생은 옛 결석 값으로 재확정해도 온라인이다")
    void 명단을_연_뒤에_온라인이_되면_지킨다() throws Exception {
        loginAsTeacher();
        givenLessonWithRoster(kim);
        OffsetDateTime loadedAt = OffsetDateTime.now().minusMinutes(30);
        var watched = savedRow(kim, AttendanceStatus.ONLINE, false);
        ReflectionTestUtils.setField(watched, "updatedAt", OffsetDateTime.now());
        given(attendanceRepository.findByLessonId(501L)).willReturn(List.of(watched));

        AttendanceConfirmResponse response = attendanceService.confirm(501L,
            new AttendanceConfirmRequest(List.of(
                new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, null)), loadedAt));

        verify(attendanceRepository).upsert(501L, 3L, 91L, LESSON_DATE, "ONLINE", null, 1L, false);
        assertThat(response.summary().online()).isEqualTo(1);
    }

    @Test
    @DisplayName("명단에 온라인이 보였는데 결석으로 바꿨으면 선생님의 되돌림이다")
    void 명단에_보이던_온라인을_바꾸면_막힌다() throws Exception {
        loginAsTeacher();
        givenLessonWithRoster(kim);
        OffsetDateTime loadedAt = OffsetDateTime.now();
        var watched = savedRow(kim, AttendanceStatus.ONLINE, false);
        ReflectionTestUtils.setField(watched, "updatedAt", loadedAt.minusHours(1));
        given(attendanceRepository.findByLessonId(501L)).willReturn(List.of(watched));

        attendanceService.confirm(501L, new AttendanceConfirmRequest(List.of(
            new AttendanceExceptionRequest(91L, AttendanceStatus.ABSENT, "안 봄")), loadedAt));

        verify(attendanceRepository).upsert(501L, 3L, 91L, LESSON_DATE, "ABSENT", "안 봄", 1L,
            true);
    }

    @Test
    @DisplayName("명단 응답은 서버 시각 loadedAt을 준다")
    void 명단은_loadedAt을_준다() {
        givenLessonWithRoster(kim);
        OffsetDateTime before = OffsetDateTime.now();

        assertThat(attendanceService.roster(501L).loadedAt()).isAfterOrEqualTo(before);
    }
}
