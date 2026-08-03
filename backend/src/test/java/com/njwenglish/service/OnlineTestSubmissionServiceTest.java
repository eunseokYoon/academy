package com.njwenglish.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.onlinetest.OnlineTestAnswerSaveRequest;
import com.njwenglish.dto.onlinetest.OnlineTestResultResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeResponse;
import com.njwenglish.dto.onlinetest.StudentOnlineTestListItemResponse;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.OnlineTestSubmission;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.repository.OnlineTestRepository;
import com.njwenglish.repository.OnlineTestSubmissionRepository;
import com.njwenglish.support.Fixtures;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlineTestSubmissionServiceTest {

    @Mock
    private OnlineTestRepository onlineTestRepository;
    @Mock
    private OnlineTestSubmissionRepository onlineTestSubmissionRepository;
    @Mock
    private PresignedUrlProvider presignedUrlProvider;
    @Mock
    private ScoreService scoreService;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private OnlineTestSubmissionService onlineTestSubmissionService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Teacher teacher = Fixtures.teacherEntity(1L);
    private final Student me = Fixtures.student(88L, "서동환");

    @BeforeEach
    void setUp() {
        onlineTestSubmissionService = new OnlineTestSubmissionService(onlineTestRepository,
            onlineTestSubmissionRepository, presignedUrlProvider, scoreService,
            studentAccessGuard);
        given(studentAccessGuard.requireSelf()).willReturn(me);
    }

    private static Short[] answers(int count, int value) {
        Short[] array = new Short[count];
        Arrays.fill(array, (short) value);
        return array;
    }

    private OnlineTest test(ScoreType scoreType, String subject, OffsetDateTime closesAt) {
        OnlineTest test = OnlineTest.create(classRoom, teacher, "6월 2주차 단어시험",
            (short) 25, (short) 5, answers(25, 3), null, "online-tests/2026/06/key.pdf",
            scoreType, subject, null, (short) 2026, (short) 6, (short) 2, null, closesAt);
        ReflectionTestUtils.setField(test, "id", 55L);
        test.publish(OffsetDateTime.now().minusDays(1));
        return test;
    }

    private void openTest(OnlineTest test) {
        given(onlineTestRepository.findOpenForStudent(any(), any(), any()))
            .willReturn(Optional.of(test));
    }

    @Test
    @DisplayName("응시 화면 응답에 정답과 해설지 URL 필드가 없다 — null로 비우는 것도 안 된다")
    void 응시_응답에는_정답_필드가_아예_없다() {
        List<String> fields = Arrays.stream(OnlineTestTakeResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

        assertThat(fields).doesNotContain("correctChoices", "answerS3Key", "answerFileUrl");

        List<String> listFields = Arrays.stream(
                StudentOnlineTestListItemResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        assertThat(listFields).doesNotContain("correctChoices", "answerS3Key", "answerFileUrl");
    }

    @Test
    @DisplayName("응시 화면을 처음 열면 임시 저장용 행이 만들어진다")
    void 처음_열면_임시_저장_행이_생긴다() {
        OnlineTest test = test(ScoreType.WORD, "영어", null);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.empty());
        given(onlineTestSubmissionRepository.save(any())).willAnswer(i -> i.getArgument(0));

        OnlineTestTakeResponse response = onlineTestSubmissionService.take(55L);

        assertThat(response.chosenChoices()).hasSize(25).containsOnlyNulls();
        assertThat(response.questionCount()).isEqualTo((short) 25);
    }

    @Test
    @DisplayName("마감 후 제출은 400이다 — 숙제의 지각 제출 허용과 다르다")
    void 마감_후_제출은_400이다() {
        OnlineTest test = test(ScoreType.WORD, "영어", OffsetDateTime.now().minusMinutes(1));
        openTest(test);

        assertThatThrownBy(() -> onlineTestSubmissionService.submit(55L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("이미 제출한 테스트를 다시 제출하면 409다")
    void 재제출은_409다() {
        OnlineTest test = test(ScoreType.WORD, "영어", null);
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 25);
        submission.submit(OffsetDateTime.now(), new BigDecimal("92.00"), (short) 23);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));

        assertThatThrownBy(() -> onlineTestSubmissionService.submit(55L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SUBMISSION_EXISTS);
    }

    @Test
    @DisplayName("scoreType이 있으면 제출 시 scores에 반영되고 과목은 online_tests 값을 쓴다")
    void 성적_반영은_출제_시_받은_과목을_쓴다() {
        OnlineTest test = test(ScoreType.WORD, "영어", null);
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 25);
        submission.saveAnswers(answers(25, 3));
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));

        OnlineTestResultResponse response = onlineTestSubmissionService.submit(55L);

        assertThat(response.score()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.correctCount()).isEqualTo((short) 25);
        // 과목을 코드에서 지어내지 않는다. exam_date는 제출일이다
        verify(scoreService).recordFromOnlineTest(me, ScoreType.WORD, "영어",
            "6월 2주차 단어시험", new BigDecimal("100.00"), LocalDate.now(),
            (short) 2026, (short) 6, (short) 2);
    }

    @Test
    @DisplayName("scoreType이 null이면 scores에 행이 생기지 않는다 — 연습용이다")
    void 연습용은_성적에_남지_않는다() {
        OnlineTest test = test(null, null, null);
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 25);
        submission.saveAnswers(answers(25, 3));
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));

        onlineTestSubmissionService.submit(55L);

        verify(scoreService, never()).recordFromOnlineTest(any(), any(), any(), any(), any(),
            any(), anyShort(), anyShort(), anyShort());
    }

    @Test
    @DisplayName("제출 후에만 해설지 URL이 내려간다")
    void 해설지는_제출_후에만_내려간다() {
        OnlineTest test = test(null, null, null);
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 25);
        submission.saveAnswers(answers(25, 3));
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));
        given(presignedUrlProvider.readUrl("online-tests/2026/06/key.pdf"))
            .willReturn("https://s3/key.pdf?sig=x");

        assertThat(onlineTestSubmissionService.submit(55L).answerFileUrl())
            .isEqualTo("https://s3/key.pdf?sig=x");
    }

    @Test
    @DisplayName("제출 전에 결과를 조회하면 404다")
    void 제출_전_결과_조회는_404다() {
        OnlineTest test = test(null, null, null);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(OnlineTestSubmission.start(test, me, (short) 25)));

        assertThatThrownBy(() -> onlineTestSubmissionService.result(55L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("임시 저장 배열 길이가 questionCount와 다르면 400이다")
    void 임시_저장_길이가_다르면_400이다() {
        OnlineTest test = test(null, null, null);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(OnlineTestSubmission.start(test, me, (short) 25)));

        assertThatThrownBy(() -> onlineTestSubmissionService.saveAnswers(55L,
            new OnlineTestAnswerSaveRequest(answers(24, 1))))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("목록의 remainingMinutes는 서버가 계산한다 — 클라이언트 시계는 틀릴 수 있다")
    void 남은_시간은_서버가_계산한다() {
        OnlineTest test = test(null, null, OffsetDateTime.now().plusMinutes(120));
        given(onlineTestRepository.findOpenForStudent(any(), any())).willReturn(List.of(test));
        given(onlineTestSubmissionRepository.findByStudentAndTests(any(), any()))
            .willReturn(List.of());

        List<StudentOnlineTestListItemResponse> items = onlineTestSubmissionService.myTests();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).remainingMinutes()).isBetween(118L, 120L);
        assertThat(items.get(0).answeredCount()).isZero();
    }
}
