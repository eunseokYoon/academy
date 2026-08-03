package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.OnlineTestAnswerKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.onlinetest.OnlineTestCreateRequest;
import com.njwenglish.dto.onlinetest.OnlineTestResultsResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeStatus;
import com.njwenglish.dto.onlinetest.OnlineTestUpdateRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.OnlineTestSubmission;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.OnlineTestStatus;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.OnlineTestRepository;
import com.njwenglish.repository.OnlineTestSubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.support.Fixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlineTestServiceTest {

    @Mock
    private OnlineTestRepository onlineTestRepository;
    @Mock
    private OnlineTestSubmissionRepository onlineTestSubmissionRepository;
    @Mock
    private ClassRoomRepository classRoomRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private StudentAccessGuard studentAccessGuard;
    @Mock
    private OnlineTestAnswerKeys answerKeys;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;

    private OnlineTestService onlineTestService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Teacher teacher = Fixtures.teacherEntity(1L);

    @BeforeEach
    void setUp() {
        onlineTestService = new OnlineTestService(onlineTestRepository,
            onlineTestSubmissionRepository, classRoomRepository, enrollmentRepository,
            teacherRepository, studentAccessGuard, answerKeys, presignedUrlProvider);

        Fixtures.login(Fixtures.teacher(1L));
        given(teacherRepository.findByUserId(1L)).willReturn(Optional.of(teacher));
        given(classRoomRepository.findById(3L)).willReturn(Optional.of(classRoom));
        given(enrollmentRepository.findActiveStudents(anyLong(), any())).willReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Short[] answers(int count, int value) {
        Short[] array = new Short[count];
        Arrays.fill(array, (short) value);
        return array;
    }

    private OnlineTestCreateRequest request(short questionCount, Short[] correctChoices,
                                            Short[] points, ScoreType scoreType, String subject) {
        return new OnlineTestCreateRequest(3L, "6월 2주차 단어시험", questionCount, (short) 5,
            correctChoices, points, null, scoreType, subject, null,
            (short) 2026, (short) 6, (short) 2, null, null);
    }

    private OnlineTest publishedTest() {
        OnlineTest test = OnlineTest.create(classRoom, teacher, "6월 2주차 단어시험",
            (short) 25, (short) 5, answers(25, 3), null, null,
            ScoreType.WORD, "영어", null, (short) 2026, (short) 6, (short) 2, null, null);
        ReflectionTestUtils.setField(test, "id", 55L);
        test.publish(OffsetDateTime.now().minusDays(1));
        return test;
    }

    @Test
    @DisplayName("정답 배열 길이가 questionCount와 다르면 400이다")
    void 정답_길이가_다르면_400이다() {
        assertThatThrownBy(() -> onlineTestService.create(
            request((short) 25, answers(24, 3), null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        verify(onlineTestRepository, never()).save(any());
    }

    @Test
    @DisplayName("정답 값이 1~choiceCount 범위를 벗어나면 400이다")
    void 정답_값이_범위를_벗어나면_400이다() {
        Short[] correct = answers(25, 3);
        correct[7] = 6;

        assertThatThrownBy(() -> onlineTestService.create(
            request((short) 25, correct, null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("배점 배열 길이가 questionCount와 다르면 400이다")
    void 배점_길이가_다르면_400이다() {
        assertThatThrownBy(() -> onlineTestService.create(
            request((short) 25, answers(25, 3), answers(20, 1), null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("scoreType을 넣고 subject를 비우면 400이다 — scores.subject가 NOT NULL이다")
    void 성적_반영에는_과목이_필수다() {
        assertThatThrownBy(() -> onlineTestService.create(
            request((short) 25, answers(25, 3), null, ScoreType.WORD, "  ")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("성적 반영을 끄면 과목 없이도 출제된다 — 연습용 테스트다")
    void 연습용은_과목이_없어도_된다() {
        given(onlineTestRepository.save(any())).willAnswer(i -> i.getArgument(0));

        assertThat(onlineTestService.create(
            request((short) 25, answers(25, 3), null, null, null)).published()).isFalse();
    }

    @Test
    @DisplayName("공개 후 정답을 수정하면 409다 — 이미 응시한 학생 점수가 소급 변경된다")
    void 공개_후_정답_수정은_409다() {
        given(onlineTestRepository.findWithClassRoom(55L)).willReturn(Optional.of(publishedTest()));

        assertThatThrownBy(() -> onlineTestService.update(55L, new OnlineTestUpdateRequest(
            null, null, null, answers(25, 1), null, null, null, null, null, null, null,
            null, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEST_ALREADY_PUBLISHED);
    }

    @Test
    @DisplayName("공개 후에도 제목과 기간은 고칠 수 있다 — 채점 결과에 영향이 없다")
    void 공개_후_제목은_고칠_수_있다() {
        OnlineTest test = publishedTest();
        given(onlineTestRepository.findWithClassRoom(55L)).willReturn(Optional.of(test));

        onlineTestService.update(55L, new OnlineTestUpdateRequest(
            "6월 2주차 단어시험(재공지)", null, null, null, null, null, null, null,
            null, null, null, null, null, null));

        assertThat(test.getTitle()).isEqualTo("6월 2주차 단어시험(재공지)");
    }

    @Test
    @DisplayName("제출이 1건이라도 있으면 삭제가 409다")
    void 제출이_있으면_삭제할_수_없다() {
        given(onlineTestRepository.findWithClassRoom(55L)).willReturn(Optional.of(publishedTest()));
        given(onlineTestSubmissionRepository.existsByOnlineTestIdAndStatus(
            55L, OnlineTestStatus.SUBMITTED)).willReturn(true);

        assertThatThrownBy(() -> onlineTestService.delete(55L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_EXISTS);

        verify(onlineTestRepository, never()).delete(any());
    }

    @Test
    @DisplayName("결과에 미응시 학생도 포함되고 average는 제출자만으로 계산된다")
    void 결과에_미응시_학생도_포함된다() {
        OnlineTest test = publishedTest();
        Student submitted = Fixtures.student(88L, "서동환");
        Student notStarted = Fixtures.student(91L, "김하늘");

        given(onlineTestRepository.findWithClassRoom(55L)).willReturn(Optional.of(test));
        given(enrollmentRepository.findActiveStudents(anyLong(), any()))
            .willReturn(List.of(submitted, notStarted));
        given(onlineTestSubmissionRepository.findByOnlineTestId(55L))
            .willReturn(List.of(submission(test, submitted, new BigDecimal("92.00"))));

        OnlineTestResultsResponse response = onlineTestService.results(55L);

        assertThat(response.counts().total()).isEqualTo(2);
        assertThat(response.counts().submitted()).isEqualTo(1);
        assertThat(response.counts().notStarted()).isEqualTo(1);
        // 미응시 학생의 0점이 평균에 섞이면 안 된다
        assertThat(response.average()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(response.items())
            .extracting(OnlineTestResultsResponse.Item::studentId,
                OnlineTestResultsResponse.Item::status)
            .containsExactly(
                tuple(88L, OnlineTestTakeStatus.SUBMITTED),
                tuple(91L, OnlineTestTakeStatus.NOT_STARTED));
    }

    @Test
    @DisplayName("아무도 제출하지 않으면 average는 null이다")
    void 제출이_없으면_평균은_null이다() {
        OnlineTest test = publishedTest();
        given(onlineTestRepository.findWithClassRoom(55L)).willReturn(Optional.of(test));
        given(enrollmentRepository.findActiveStudents(anyLong(), any()))
            .willReturn(List.of(Fixtures.student(91L, "김하늘")));
        given(onlineTestSubmissionRepository.findByOnlineTestId(55L)).willReturn(List.of());

        assertThat(onlineTestService.results(55L).average()).isNull();
    }

    @Test
    @DisplayName("서버가 발급하지 않은 해설지 키는 400이다")
    void 위조된_해설지_키는_400이다() {
        given(answerKeys.matches("online-tests/2026/06/남의파일.pdf", 1L)).willReturn(false);

        assertThatThrownBy(() -> onlineTestService.create(new OnlineTestCreateRequest(
            3L, "6월 2주차 단어시험", (short) 25, (short) 5, answers(25, 3), null,
            "online-tests/2026/06/남의파일.pdf", null, null, null,
            (short) 2026, (short) 6, (short) 2, null, null)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    private OnlineTestSubmission submission(OnlineTest test, Student student, BigDecimal score) {
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, student, (short) 25);
        submission.submit(OffsetDateTime.now(), score, (short) 23);
        return submission;
    }
}
