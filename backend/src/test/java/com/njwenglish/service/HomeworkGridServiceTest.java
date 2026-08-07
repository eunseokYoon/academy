package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.dto.homework.HomeworkGridResponse;
import com.njwenglish.dto.homework.HomeworkGridSaveRequest;
import com.njwenglish.dto.homework.HomeworkUpdateRequest;
import com.njwenglish.dto.homework.ResubmitOpenResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Homework;
import com.njwenglish.entity.Lesson;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Submission;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.HomeworkResult;
import com.njwenglish.entity.enums.SubmissionStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.HomeworkRepository;
import com.njwenglish.repository.LessonRepository;
import com.njwenglish.repository.SubmissionPhotoRepository;
import com.njwenglish.repository.SubmissionPhotoRepository.PhotoCountRow;
import com.njwenglish.repository.SubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.time.LocalDate;
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

    @Test
    @DisplayName("재제출을 안 연 열은 세모·X가 있어도 대상 수가 0이고 마감이 없다")
    void resubmitTargetCountZeroWhenNotOpened() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission partial = Fixtures.submission(1L, column, goYeonJun);
        partial.grade(HomeworkResult.PARTIAL, (short) 60);
        Submission notDone = Fixtures.submission(2L, column, kwonTaeHo);
        notDone.grade(HomeworkResult.NOT_DONE, null);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L))
            .willReturn(List.of(partial, notDone));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);

        assertThat(response.columns().get(0).resubmitDueAt()).isNull();
        assertThat(response.columns().get(0).resubmitTargetCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("사진 수는 제출물 id에 맞춰 정확한 칸에 반영된다")
    void photoCountAppliedPerSubmission() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission first = Fixtures.submission(1L, column, goYeonJun);
        Submission second = Fixtures.submission(2L, column, kwonTaeHo);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(first, second));
        // 2번 제출물에만 사진 3장 — countBySubmissionIds는 사진이 있는 행만 GROUP BY로 돌려준다.
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L)))
            .willReturn(List.of(photoCountRow(2L, 3)));

        HomeworkGridResponse response = homeworkService.grid(501L);

        List<HomeworkGridResponse.CellInfo> cells = response.columns().get(0).cells();
        assertThat(cells.get(0).photoCount()).isEqualTo(0);
        assertThat(cells.get(1).photoCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("부분완료 비율·재제출 확인 표시·제출 상태·확인대기 수가 칸에 그대로 옮겨진다")
    void cellCarriesCompletionRateStatusAndResolvedFlag() {
        Student parkSeoJun = Fixtures.student(95L, "박서준");
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(
            LESSON_DATE.plusDays(7).atTime(21, 0).atOffset(java.time.ZoneOffset.ofHours(9)));

        Submission partial = Fixtures.submission(1L, column, goYeonJun);
        partial.grade(HomeworkResult.PARTIAL, (short) 70);

        Submission submitted = Fixtures.submission(2L, column, kwonTaeHo);
        submitted.submit(OffsetDateTime.now(), false);

        Submission resolved = Fixtures.submission(3L, column, parkSeoJun);
        resolved.resolveByResubmission();

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo, parkSeoJun));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L))
            .willReturn(List.of(partial, submitted, resolved));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L, 3L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);
        List<HomeworkGridResponse.CellInfo> cells = response.columns().get(0).cells();

        HomeworkGridResponse.CellInfo partialCell = cells.get(0);
        assertThat(partialCell.result()).isEqualTo(HomeworkResult.PARTIAL);
        assertThat(partialCell.completionRate()).isEqualTo((short) 70);
        assertThat(partialCell.resolvedByResubmission()).isFalse();

        HomeworkGridResponse.CellInfo submittedCell = cells.get(1);
        assertThat(submittedCell.status()).isEqualTo(SubmissionStatus.SUBMITTED);

        HomeworkGridResponse.CellInfo resolvedCell = cells.get(2);
        assertThat(resolvedCell.result()).isEqualTo(HomeworkResult.DONE);
        assertThat(resolvedCell.resolvedByResubmission()).isTrue();

        assertThat(response.columns().get(0).awaitingCheckCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("재제출 대상 수는 명단에서 빠진 학생(퇴원 등)의 옛 칸을 세지 않는다")
    void resubmitTargetCountExcludesOffRosterCells() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(
            LESSON_DATE.plusDays(7).atTime(21, 0).atOffset(java.time.ZoneOffset.ofHours(9)));

        // 고연준은 재원생 명단에 있고 이미 통과했다.
        Submission onRoster = Fixtures.submission(1L, column, goYeonJun);
        onRoster.grade(HomeworkResult.DONE, null);
        // 권태호는 이 반을 나가 findActiveStudents엔 더 이상 없지만, 숙제 데이터는 지우지 않으므로
        // (StudentService 퇴원 처리) 옛 제출 행은 세모·X 상태로 그대로 남아 있다. 이 칸이
        // 대상 수에 섞이면 화면(cells)엔 없는 학생이 배지 숫자에만 나타난다.
        Submission offRoster = Fixtures.submission(2L, column, kwonTaeHo);
        offRoster.grade(HomeworkResult.NOT_DONE, null);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L))
            .willReturn(List.of(onRoster, offRoster));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L, 2L)))
            .willReturn(List.of());

        HomeworkGridResponse response = homeworkService.grid(501L);

        assertThat(response.columns().get(0).cells()).hasSize(1);
        assertThat(response.columns().get(0).resubmitTargetCount()).isEqualTo(0);
        assertThat(response.columns().get(0).awaitingCheckCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("새 열을 저장하면 수업일 재원생 전원의 칸이 미채점으로 깔린다")
    void saveGridCreatesColumnAndCells() {
        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.save(any(Homework.class))).willAnswer(invocation -> {
            Homework saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 720L);
            return saved;
        });
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of());
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of());

        HomeworkGridSaveRequest request = new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(null, "독해 5-8", (short) 1, List.of())));

        homeworkService.saveGrid(request);

        ArgumentCaptor<List<Submission>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).allMatch(cell -> cell.getResult() == null);
        assertThat(captor.getValue()).extracting(cell -> cell.getStudent().getName())
            .containsExactly("고연준", "권태호");
    }

    @Test
    @DisplayName("새 열과 그 채점을 한 요청에 같이 보내면 그 자리에서 반영된다")
    void saveGridGradesNewColumnCellsInSameRequest() {
        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.save(any(Homework.class))).willAnswer(invocation -> {
            Homework saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 720L);
            return saved;
        });
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of());
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of());

        // 새 열(homeworkId=null)인데 고연준 칸에 바로 DONE을 채점해서 보낸다.
        HomeworkGridSaveRequest request = new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(null, "독해 5-8", (short) 1, List.of(
                new HomeworkGridSaveRequest.Cell(88L, HomeworkResult.DONE, null)))));

        homeworkService.saveGrid(request);

        ArgumentCaptor<List<Submission>> captor = ArgumentCaptor.forClass(List.class);
        verify(submissionRepository).saveAll(captor.capture());
        Submission goYeonJunCell = captor.getValue().stream()
            .filter(cell -> cell.getStudent().getId().equals(88L))
            .findFirst().orElseThrow();
        Submission kwonTaeHoCell = captor.getValue().stream()
            .filter(cell -> cell.getStudent().getId().equals(91L))
            .findFirst().orElseThrow();

        assertThat(goYeonJunCell.getResult()).isEqualTo(HomeworkResult.DONE);
        assertThat(kwonTaeHoCell.getResult()).isNull();
    }

    @Test
    @DisplayName("기존 열의 칸에 세모와 퍼센트를 저장한다")
    void saveGridGradesCells() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission cell = Fixtures.submission(1L, column, goYeonJun);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(cell));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L))).willReturn(List.of());

        HomeworkGridSaveRequest request = new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(720L, "독해 5-8 (수정)", (short) 1, List.of(
                new HomeworkGridSaveRequest.Cell(88L, HomeworkResult.PARTIAL, (short) 50)))));

        homeworkService.saveGrid(request);

        assertThat(cell.getResult()).isEqualTo(HomeworkResult.PARTIAL);
        assertThat(cell.getCompletionRate()).isEqualTo((short) 50);
        assertThat(column.getTitle()).isEqualTo("독해 5-8 (수정)");
    }

    @Test
    @DisplayName("result를 null로 보내면 행은 남고 미채점으로 되돌아간다")
    void saveGridClearsResult() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission cell = Fixtures.submission(1L, column, goYeonJun);
        cell.grade(HomeworkResult.NOT_DONE, null);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(cell));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L))).willReturn(List.of());

        homeworkService.saveGrid(new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(720L, "독해 5-8", (short) 1, List.of(
                new HomeworkGridSaveRequest.Cell(88L, null, null))))));

        assertThat(cell.getResult()).isNull();
    }

    @Test
    @DisplayName("명단에 있는데 칸이 없는 학생은 채점하면 칸이 만들어진다")
    void saveGridCreatesMissingCellForRosterStudent() {
        // 열을 만든 뒤 잘못된 퇴원이 정정되면 이 상태가 된다.
        // grid()가 빈 칸을 내려주므로 화면에는 채점 칸이 뜬다 — 저장이 조용히 버리면 안 된다
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission onlyCell = Fixtures.submission(1L, column, goYeonJun);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun, kwonTaeHo));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(onlyCell));
        given(submissionRepository.save(any(Submission.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L))).willReturn(List.of());

        homeworkService.saveGrid(new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(720L, "독해 5-8", (short) 1, List.of(
                new HomeworkGridSaveRequest.Cell(91L, HomeworkResult.NOT_DONE, null))))));

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        assertThat(captor.getValue().getStudent().getId()).isEqualTo(91L);
        assertThat(captor.getValue().getResult()).isEqualTo(HomeworkResult.NOT_DONE);
    }

    @Test
    @DisplayName("명단에도 없는 학생의 칸은 만들지 않고 건너뛴다")
    void saveGridSkipsOffRosterStudent() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission onlyCell = Fixtures.submission(1L, column, goYeonJun);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(enrollmentRepository.findActiveStudents(3L, LESSON_DATE))
            .willReturn(List.of(goYeonJun));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(column));
        given(submissionRepository.findByLessonForGrid(501L)).willReturn(List.of(onlyCell));
        given(submissionPhotoRepository.countBySubmissionIds(List.of(1L))).willReturn(List.of());

        homeworkService.saveGrid(new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(720L, "독해 5-8", (short) 1, List.of(
                new HomeworkGridSaveRequest.Cell(91L, HomeworkResult.NOT_DONE, null))))));

        verify(submissionRepository, never()).save(any(Submission.class));
    }

    @Test
    @DisplayName("다른 수업의 열을 저장하려 하면 400이다")
    void saveGridRejectsForeignColumn() {
        ClassRoom other = ClassRoom.create(teacher, "다른 반", "ZZ99ZZ", null);
        ReflectionTestUtils.setField(other, "id", 9L);
        Lesson otherLesson = Fixtures.lesson(999L, other, LESSON_DATE);
        Homework foreign = Fixtures.gridColumn(888L, other, otherLesson, "남의 열", (short) 1);

        given(lessonRepository.findById(501L)).willReturn(Optional.of(lesson));
        given(homeworkRepository.findGridColumns(501L)).willReturn(List.of(foreign));

        HomeworkGridSaveRequest request = new HomeworkGridSaveRequest(501L, List.of(
            new HomeworkGridSaveRequest.Column(888L, "남의 열", (short) 1, List.of())));

        assertThatThrownBy(() -> homeworkService.saveGrid(request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("재제출 요청은 세모·X 학생만 대상으로 잡고 마감을 붙인다")
    void openResubmit() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission done = Fixtures.submission(1L, column, goYeonJun);
        done.grade(HomeworkResult.DONE, null);
        Submission partial = Fixtures.submission(2L, column, kwonTaeHo);
        partial.grade(HomeworkResult.PARTIAL, (short) 50);

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.findResubmitTargets(720L)).willReturn(List.of(partial));

        OffsetDateTime dueAt = OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9));
        ResubmitOpenResponse response = homeworkService.openResubmit(720L, dueAt);

        assertThat(response.targetCount()).isEqualTo(1);
        assertThat(response.dueAt()).isEqualTo(dueAt);
        assertThat(column.isResubmitOpen()).isTrue();
        assertThat(partial.isResubmitTarget()).isTrue();
        assertThat(done.isResubmitTarget()).isFalse();
    }

    @Test
    @DisplayName("마감을 생략하면 다음 수업일 21시로 잡는다")
    void openResubmitDefaultsToNextLesson() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        Submission partial = Fixtures.submission(2L, column, kwonTaeHo);
        partial.grade(HomeworkResult.PARTIAL, (short) 50);

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.findResubmitTargets(720L)).willReturn(List.of(partial));
        given(lessonRepository.findNextLessonDates(eq(3L), any(LocalDate.class)))
            .willReturn(List.of(LocalDate.of(2026, 8, 5)));

        ResubmitOpenResponse response = homeworkService.openResubmit(720L, null);

        assertThat(response.dueAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(response.dueAt().toLocalTime().getHour()).isEqualTo(21);
    }

    @Test
    @DisplayName("세모·X가 없는 열의 재제출 요청은 409다")
    void openResubmitWithoutTarget() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.findResubmitTargets(720L)).willReturn(List.of());

        assertThatThrownBy(() -> homeworkService.openResubmit(720L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NO_RESUBMIT_TARGET);
    }

    @Test
    @DisplayName("ONLINE 숙제에 재제출을 열려고 하면 400이다")
    void openResubmitRejectsOnlineHomework() {
        Homework online = Fixtures.homework(700L, classRoom,
            OffsetDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZoneOffset.ofHours(9)));

        given(homeworkRepository.findWithClassRoom(700L)).willReturn(Optional.of(online));

        assertThatThrownBy(() -> homeworkService.openResubmit(700L, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("이미 열린 재제출의 마감을 앞당기면 400이다")
    void openResubmitCannotShortenDueAt() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));
        Submission partial = Fixtures.submission(2L, column, kwonTaeHo);
        partial.grade(HomeworkResult.PARTIAL, (short) 50);

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.findResubmitTargets(720L)).willReturn(List.of(partial));

        OffsetDateTime earlier = OffsetDateTime.of(2026, 8, 1, 20, 0, 0, 0, ZoneOffset.ofHours(9));

        assertThatThrownBy(() -> homeworkService.openResubmit(720L, earlier))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("재제출을 취소하면 마감이 사라진다")
    void closeResubmit() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.countByHomeworkIdAndStatusNot(720L, SubmissionStatus.NOT_SUBMITTED))
            .willReturn(0L);

        homeworkService.closeResubmit(720L);

        assertThat(column.isResubmitOpen()).isFalse();
    }

    @Test
    @DisplayName("이미 낸 학생이 있으면 재제출 취소는 409다")
    void closeResubmitBlockedBySubmission() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);
        column.openResubmit(OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));
        given(submissionRepository.countByHomeworkIdAndStatusNot(720L, SubmissionStatus.NOT_SUBMITTED))
            .willReturn(3L);

        assertThatThrownBy(() -> homeworkService.closeResubmit(720L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_EXISTS);
    }

    @Test
    @DisplayName("그리드 열은 PATCH로 마감을 받지 않는다 — 재제출 경로 전용이라 400이다")
    void updateRejectsDueAtOnGridColumn() {
        Homework column = Fixtures.gridColumn(720L, classRoom, lesson, "독해 5-8", (short) 1);

        given(homeworkRepository.findWithClassRoom(720L)).willReturn(Optional.of(column));

        HomeworkUpdateRequest request = new HomeworkUpdateRequest("독해 5-8", null, null,
            OffsetDateTime.of(2026, 8, 5, 20, 0, 0, 0, ZoneOffset.ofHours(9)));

        assertThatThrownBy(() -> homeworkService.update(720L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        // dueAt이 그대로 null이어야 한다 — 거부 전에 다른 필드가 먼저 바뀌면 안 된다.
        assertThat(column.getDueAt()).isNull();
    }

    private PhotoCountRow photoCountRow(Long submissionId, long photoCount) {
        return new PhotoCountRow() {
            @Override
            public Long getSubmissionId() {
                return submissionId;
            }

            @Override
            public long getPhotoCount() {
                return photoCount;
            }
        };
    }
}
