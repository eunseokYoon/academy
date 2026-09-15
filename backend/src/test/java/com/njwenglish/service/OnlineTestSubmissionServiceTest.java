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
    private WeeklyTestService weeklyTestService;
    @Mock
    private StudentAccessGuard studentAccessGuard;

    private OnlineTestSubmissionService onlineTestSubmissionService;

    private final ClassRoom classRoom = Fixtures.openClassRoom(3L, "고2 심화반", "HK7F2Q");
    private final Teacher teacher = Fixtures.teacherEntity(1L);
    private final Student me = Fixtures.student(88L, "서동환");

    @BeforeEach
    void setUp() {
        onlineTestSubmissionService = new OnlineTestSubmissionService(onlineTestRepository,
            onlineTestSubmissionRepository, presignedUrlProvider, weeklyTestService,
            studentAccessGuard);
        given(studentAccessGuard.requireSelf()).willReturn(me);
    }

    private static Short[] answers(int count, int value) {
        Short[] array = new Short[count];
        Arrays.fill(array, (short) value);
        return array;
    }

    private OnlineTest test(OffsetDateTime closesAt) {
        OnlineTest test = OnlineTest.create(classRoom, teacher, "6월 2주차 단어시험",
            (short) 25, (short) 5, answers(25, 3), null, "online-tests/2026/06/key.pdf",
            null, (short) 2026, (short) 6, (short) 2, null, closesAt);
        ReflectionTestUtils.setField(test, "id", 55L);
        test.publish(OffsetDateTime.now().minusDays(1));
        return test;
    }

    /** 5문항, 앞 3문항이 내부지문. 정답은 {1,2,3,1,2}다. */
    private OnlineTest splitTest() {
        OnlineTest test = OnlineTest.create(classRoom, teacher, "6월 2주차 클리닉",
            (short) 5, (short) 5, new Short[] {1, 2, 3, 1, 2}, null,
            "online-tests/2026/06/key.pdf", (short) 3,
            (short) 2026, (short) 6, (short) 2, null, null);
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
        OnlineTest test = test(null);
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
        OnlineTest test = test(OffsetDateTime.now().minusMinutes(1));
        openTest(test);

        assertThatThrownBy(() -> onlineTestSubmissionService.submit(55L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("이미 제출한 테스트를 다시 제출하면 409다")
    void 재제출은_409다() {
        OnlineTest test = test(null);
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
    @DisplayName("제출 후에만 해설지 URL이 내려간다")
    void 해설지는_제출_후에만_내려간다() {
        OnlineTest test = test(null);
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
        OnlineTest test = test(null);
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
        OnlineTest test = test(null);
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
        OnlineTest test = test(OffsetDateTime.now().plusMinutes(120));
        given(onlineTestRepository.findOpenForStudent(any(), any())).willReturn(List.of(test));
        given(onlineTestSubmissionRepository.findByStudentAndTests(any(), any()))
            .willReturn(List.of());

        List<StudentOnlineTestListItemResponse> items = onlineTestSubmissionService.myTests();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).remainingMinutes()).isBetween(118L, 120L);
        assertThat(items.get(0).answeredCount()).isZero();
    }

    /**
     * 11번. 점수 대신 개수를 보여주려면 내부·외부가 응답에 있어야 한다.
     * 집계는 OnlineTestService.countCorrect 한 곳이다 — 두 곳에서 세면 갈라진다.
     */
    @Test
    @DisplayName("학생 결과에 내부·외부 맞은 개수가 들어간다")
    void 학생_결과에_내부_외부가_있다() {
        // 정답 {1,2,3,1,2} / 학생 {1,2,9,1,9} → 내부(앞 3) 2개, 외부(뒤 2) 1개
        OnlineTest test = splitTest();
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 5);
        submission.saveAnswers(new Short[] {1, 2, 9, 1, 9});
        submission.submit(OffsetDateTime.now(), new BigDecimal("60.00"), (short) 3);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));

        OnlineTestResultResponse result = onlineTestSubmissionService.result(55L);

        assertThat(result.internalQuestionCount()).isEqualTo((short) 3);
        assertThat(result.internalCorrect()).isEqualTo((short) 2);
        assertThat(result.externalCorrect()).isEqualTo((short) 1);
    }

    /**
     * 내부지문 문항 수가 없으면 둘 다 null이다. <b>0으로 채우지 마라</b> —
     * "0개 맞음"으로 읽힌다. 화면은 총 개수만 보여준다.
     */
    @Test
    @DisplayName("내부지문 문항 수가 없으면 내부·외부는 null이다")
    void 내부지문_수가_없으면_null이다() {
        // test(null)은 내부지문 문항 수가 없는 25문항 테스트다
        OnlineTest test = test(null);
        OnlineTestSubmission submission = OnlineTestSubmission.start(test, me, (short) 25);
        submission.saveAnswers(answers(25, 3));
        submission.submit(OffsetDateTime.now(), new BigDecimal("100.00"), (short) 25);
        openTest(test);
        given(onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(55L, 88L))
            .willReturn(Optional.of(submission));

        OnlineTestResultResponse result = onlineTestSubmissionService.result(55L);

        // 0으로 채우지 마라 — "0개 맞음"으로 읽힌다. 화면은 총 개수만 보여준다
        assertThat(result.internalQuestionCount()).isNull();
        assertThat(result.internalCorrect()).isNull();
        assertThat(result.externalCorrect()).isNull();
        assertThat(result.correctCount()).isEqualTo((short) 25);
    }
}
