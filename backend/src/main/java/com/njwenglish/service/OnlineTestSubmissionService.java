package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.OnlineTestScoring;
import com.njwenglish.dto.onlinetest.OnlineTestAnswerSaveRequest;
import com.njwenglish.dto.onlinetest.OnlineTestResultResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeStatus;
import com.njwenglish.dto.onlinetest.StudentOnlineTestListItemResponse;
import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.OnlineTestSubmission;
import com.njwenglish.entity.Student;
import com.njwenglish.repository.OnlineTestRepository;
import com.njwenglish.repository.OnlineTestSubmissionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-10 응시. 학생은 종이 시험지를 풀고 <b>답만</b> 입력한다.
 *
 * <p>정답과 해설지가 새면 기능 전체가 무의미해진다. 응시 경로에서 내려가는 DTO는
 * OnlineTestTakeResponse와 StudentOnlineTestListItemResponse 둘뿐이고,
 * 둘 다 correctChoices·answerFileUrl 필드가 없다. 편의를 이유로 필드를 추가하지 마라.
 */
@Service
@RequiredArgsConstructor
public class OnlineTestSubmissionService {

    /** 채점 결과를 scores에 남길 때 exam_date로 쓴다. 제출일 기준이다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OnlineTestRepository onlineTestRepository;
    private final OnlineTestSubmissionRepository onlineTestSubmissionRepository;
    private final PresignedUrlProvider presignedUrlProvider;
    private final ScoreService scoreService;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional(readOnly = true)
    public List<StudentOnlineTestListItemResponse> myTests() {
        Student me = studentAccessGuard.requireSelf();
        OffsetDateTime now = OffsetDateTime.now();

        List<OnlineTest> tests = onlineTestRepository.findOpenForStudent(me.getId(), now);
        if (tests.isEmpty()) {
            return List.of();
        }
        Map<Long, OnlineTestSubmission> submissions = onlineTestSubmissionRepository
            .findByStudentAndTests(me.getId(), tests.stream().map(OnlineTest::getId).toList())
            .stream()
            .collect(Collectors.toMap(s -> s.getOnlineTest().getId(), Function.identity()));

        return tests.stream()
            .map(test -> {
                OnlineTestSubmission submission = submissions.get(test.getId());
                return new StudentOnlineTestListItemResponse(
                    test.getId(), test.getTitle(), test.getClassRoom().getName(),
                    test.getQuestionCount(), test.getClosesAt(),
                    remainingMinutes(test.getClosesAt(), now),
                    OnlineTestTakeStatus.of(submission),
                    answeredCount(submission));
            })
            .toList();
    }

    /**
     * 응시 화면. 처음 열면 임시 저장용 행을 만든다.
     * 이미 제출했으면 답이 그대로 내려가고 화면은 읽기 전용이 된다.
     */
    @Transactional
    public OnlineTestTakeResponse take(Long testId) {
        Student me = studentAccessGuard.requireSelf();
        OnlineTest test = findOpen(testId, me.getId());
        OnlineTestSubmission submission = onlineTestSubmissionRepository
            .findByOnlineTestIdAndStudentId(testId, me.getId())
            .orElseGet(() -> onlineTestSubmissionRepository.save(
                OnlineTestSubmission.start(test, me, test.getQuestionCount())));

        return new OnlineTestTakeResponse(
            test.getId(), test.getTitle(), test.getClassRoom().getName(),
            test.getQuestionCount(), test.getChoiceCount(), test.getClosesAt(),
            submission.getChosenChoices(), OnlineTestTakeStatus.of(submission));
    }

    /** 임시 저장. 채점하지 않고 IN_PROGRESS를 유지한다. */
    @Transactional
    public void saveAnswers(Long testId, OnlineTestAnswerSaveRequest request) {
        Student me = studentAccessGuard.requireSelf();
        OnlineTest test = findOpen(testId, me.getId());
        requireOpen(test);

        OnlineTestSubmission submission = findSubmission(testId, me.getId());
        if (submission.isSubmitted()) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }
        validateAnswers(request.chosenChoices(), test);
        submission.saveAnswers(request.chosenChoices());
    }

    /**
     * 제출 + 즉시 채점. 한 트랜잭션이다.
     *
     * <ol>
     *   <li>마감 경과면 400, 이미 제출했으면 409
     *   <li>채점해서 score·correctCount·submittedAt 기록
     *   <li>scoreType이 있으면 scores에 행 생성 → P-4 그래프에 얹힌다
     * </ol>
     *
     * <p>요청 본문이 없다. 마지막으로 임시 저장한 답안으로 채점한다.
     */
    @Transactional
    public OnlineTestResultResponse submit(Long testId) {
        Student me = studentAccessGuard.requireSelf();
        OnlineTest test = findOpen(testId, me.getId());
        requireOpen(test);

        OnlineTestSubmission submission = findSubmission(testId, me.getId());
        if (submission.isSubmitted()) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }

        OffsetDateTime now = OffsetDateTime.now();
        OnlineTestScoring.Result result = OnlineTestScoring.grade(
            test.getCorrectChoices(), submission.getChosenChoices(), test.getPoints());
        submission.submit(now, result.score(), result.correctCount());

        // subject·examDate는 online_tests에 자동으로 존재하지 않는다. 여기서 채우지 않으면
        // scores의 NOT NULL 제약에 걸린다. subject를 코드에서 지어내지 마라
        if (test.reflectsToScores()) {
            scoreService.recordFromOnlineTest(me, test.getScoreType(), test.getSubject(),
                test.getTitle(), result.score(), now.atZoneSameInstant(KST).toLocalDate(),
                test.getYear(), test.getMonth(), test.getWeek());
        }

        return toResult(test, submission);
    }

    /** 제출 후 결과 재조회. 제출 전이면 404다. */
    @Transactional(readOnly = true)
    public OnlineTestResultResponse result(Long testId) {
        Student me = studentAccessGuard.requireSelf();
        OnlineTest test = findOpen(testId, me.getId());
        OnlineTestSubmission submission = findSubmission(testId, me.getId());
        if (!submission.isSubmitted()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toResult(test, submission);
    }

    // ---------- 내부 ----------

    /**
     * 제출 후에만 정답과 해설지 URL이 붙는다.
     * 이 메서드를 응시 화면에서 호출하지 마라.
     */
    private OnlineTestResultResponse toResult(OnlineTest test, OnlineTestSubmission submission) {
        Short[] correct = test.getCorrectChoices();
        Short[] chosen = submission.getChosenChoices();

        List<OnlineTestResultResponse.QuestionResult> results = new ArrayList<>(correct.length);
        for (int i = 0; i < correct.length; i++) {
            Short picked = chosen == null || i >= chosen.length ? null : chosen[i];
            results.add(new OnlineTestResultResponse.QuestionResult(
                i + 1, picked, correct[i], picked != null && picked.equals(correct[i])));
        }

        return new OnlineTestResultResponse(
            test.getId(), test.getTitle(), submission.getScore(), submission.getCorrectCount(),
            test.getQuestionCount(), submission.getSubmittedAt(),
            test.getAnswerS3Key() == null
                ? null : presignedUrlProvider.readUrl(test.getAnswerS3Key()),
            results);
    }

    private OnlineTest findOpen(Long testId, Long studentId) {
        return onlineTestRepository.findOpenForStudent(testId, studentId, OffsetDateTime.now())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private OnlineTestSubmission findSubmission(Long testId, Long studentId) {
        return onlineTestSubmissionRepository.findByOnlineTestIdAndStudentId(testId, studentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 마감 후 제출은 400이다. 숙제(지각 제출 허용)와 다르다 — 시험이라 마감이 마감이다. */
    private void requireOpen(OnlineTest test) {
        if (test.isClosedAt(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** 길이와 값 범위만 본다. 안 푼 문항은 null이고 그건 정상이다. */
    private void validateAnswers(Short[] chosenChoices, OnlineTest test) {
        if (chosenChoices == null || chosenChoices.length != test.getQuestionCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        for (Short choice : chosenChoices) {
            if (choice != null && (choice < 1 || choice > test.getChoiceCount())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    private int answeredCount(OnlineTestSubmission submission) {
        if (submission == null || submission.getChosenChoices() == null) {
            return 0;
        }
        int count = 0;
        for (Short choice : submission.getChosenChoices()) {
            if (choice != null) {
                count++;
            }
        }
        return count;
    }

    /** 서버가 계산한다. 클라이언트 시계는 틀릴 수 있다. 음수면 마감 경과다. */
    private Long remainingMinutes(OffsetDateTime closesAt, OffsetDateTime now) {
        return closesAt == null ? null : ChronoUnit.MINUTES.between(now, closesAt);
    }
}
