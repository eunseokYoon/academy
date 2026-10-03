package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.s3.OnlineTestAnswerKeys;
import com.njwenglish.common.s3.PresignedUrlProvider;
import com.njwenglish.common.security.CurrentUser;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.onlinetest.AnswerUploadUrlRequest;
import com.njwenglish.dto.onlinetest.AnswerUploadUrlResponse;
import com.njwenglish.dto.onlinetest.OnlineTestCreateRequest;
import com.njwenglish.dto.onlinetest.OnlineTestCreateResponse;
import com.njwenglish.dto.onlinetest.OnlineTestDetailResponse;
import com.njwenglish.dto.onlinetest.OnlineTestListItemResponse;
import com.njwenglish.dto.onlinetest.OnlineTestResultsResponse;
import com.njwenglish.dto.onlinetest.OnlineTestStudentDetailResponse;
import com.njwenglish.dto.onlinetest.OnlineTestTakeStatus;
import com.njwenglish.dto.onlinetest.OnlineTestUpdateRequest;
import com.njwenglish.dto.weeklytest.ClinicReflection;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.OnlineTestSubmission;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.Teacher;
import com.njwenglish.entity.enums.OnlineTestStatus;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.OnlineTestRepository;
import com.njwenglish.repository.OnlineTestSubmissionRepository;
import com.njwenglish.repository.TeacherRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * T-14 온라인 테스트 관리. 시험은 <b>종이로</b> 보고 학생은 답만 웹에 입력한다.
 * 문제지 파일을 저장하지 마라 — 서버가 가진 건 정답 배열과 해설지뿐이다.
 *
 * <p>시간 제한 타이머·문제 순서 섞기·부정행위 감지는 범위 밖이다.
 * 주관식·서술형도 만들지 마라. 오지선다(정확히는 2~10지선다)만이다.
 */
@Service
@RequiredArgsConstructor
public class OnlineTestService {

    /** 해설지는 문서 한 장이다. 자료실 상한과 같은 50MB면 충분하다. */
    private static final long MAX_ANSWER_BYTES = 50L * 1024 * 1024;

    /** 해설지 장수 상한(2026-09-29). 공지 첨부(NoticeService.MAX_ATTACHMENTS)와 같다. */
    private static final int MAX_ANSWER_FILES = 5;

    private final OnlineTestRepository onlineTestRepository;
    private final OnlineTestSubmissionRepository onlineTestSubmissionRepository;
    private final ClassRoomRepository classRoomRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final WeeklyTestService weeklyTestService;
    private final OnlineTestAnswerKeys answerKeys;
    private final PresignedUrlProvider presignedUrlProvider;
    private final WeeklyTestScoreRepository weeklyTestScoreRepository;

    @Transactional(readOnly = true)
    public List<OnlineTestListItemResponse> list(Long classRoomId, Short year, Short month,
                                                 Short week) {
        return onlineTestRepository.search(classRoomId, year, month, week).stream()
            .map(OnlineTestListItemResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OnlineTestDetailResponse detail(Long testId) {
        OnlineTest test = findTest(testId);
        return OnlineTestDetailResponse.from(test, answerFiles(test));
    }

    @Transactional
    public OnlineTestCreateResponse create(OnlineTestCreateRequest request) {
        Teacher teacher = currentTeacher();
        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        short questionCount = validQuestionCount(request.questionCount());
        short choiceCount = validChoiceCount(request.choiceCount());
        validateChoices(request.correctChoices(), questionCount, choiceCount);
        validatePoints(request.points(), questionCount);
        validateWeek(request.year(), request.month(), request.week());
        List<String> answerS3Keys = validAnswerKeys(
            request.answerS3Keys() != null ? request.answerS3Keys() : single(request.answerS3Key()),
            teacher.getId());
        validatePeriod(request.opensAt(), request.closesAt());
        validateInternalCount(request.internalQuestionCount(), questionCount);

        OnlineTest test = onlineTestRepository.save(OnlineTest.create(
            classRoom, teacher, request.title().trim(), questionCount, choiceCount,
            request.correctChoices(), request.points(), answerS3Keys,
            request.internalQuestionCount(),
            request.year(), request.month(), request.week(),
            request.opensAt(), request.closesAt()));

        return new OnlineTestCreateResponse(test.getId(),
            enrollmentRepository.findActiveStudents(classRoom.getId(), LocalDate.now()).size(),
            false);
    }

    /**
     * 공개 후에는 정답·문항 수·배점을 바꿀 수 없다 (409).
     * 이미 응시한 학생의 점수가 소급 변경되기 때문이다. 고치려면 삭제 후 재출제한다.
     */
    @Transactional
    public OnlineTestDetailResponse update(Long testId, OnlineTestUpdateRequest request) {
        OnlineTest test = findTest(testId);
        Teacher teacher = currentTeacher();
        String title = request.title() == null ? test.getTitle() : request.title().trim();
        // 새 화면은 answerS3Keys(null=그대로, []=전부 삭제), 옛 화면은 새로 올린 한 장만 보낸다
        List<String> answerS3Keys = request.answerS3Keys() != null
            ? validAnswerKeys(request.answerS3Keys(), teacher.getId())
            : request.answerS3Key() != null
                ? validAnswerKeys(single(request.answerS3Key()), teacher.getId())
                : test.getAnswerS3Keys();
        List<String> removed = test.getAnswerS3Keys().stream()
            .filter(key -> !answerS3Keys.contains(key)).toList();
        OffsetDateTime opensAt = request.opensAt() == null ? test.getOpensAt() : request.opensAt();
        OffsetDateTime closesAt = request.closesAt() == null
            ? test.getClosesAt() : request.closesAt();
        validatePeriod(opensAt, closesAt);

        if (test.isPublished()) {
            requireNoScoringChange(test, request);
            test.editSchedule(title, answerS3Keys, opensAt, closesAt);
            deleteFilesAfterCommit(removed);
            return OnlineTestDetailResponse.from(test, answerFiles(test));
        }

        short questionCount = request.questionCount() == null
            ? test.getQuestionCount() : validQuestionCount(request.questionCount());
        short choiceCount = request.choiceCount() == null
            ? test.getChoiceCount() : validChoiceCount(request.choiceCount());
        Short[] correctChoices = request.correctChoices() == null
            ? test.getCorrectChoices() : request.correctChoices();
        Short[] points = request.points() == null ? test.getPoints() : request.points();
        short year = request.year() == null ? test.getYear() : request.year();
        short month = request.month() == null ? test.getMonth() : request.month();
        short week = request.week() == null ? test.getWeek() : request.week();
        Short internalQuestionCount = request.internalQuestionCount() == null
            ? test.getInternalQuestionCount() : request.internalQuestionCount();

        validateChoices(correctChoices, questionCount, choiceCount);
        validatePoints(points, questionCount);
        validateWeek(year, month, week);
        validateInternalCount(internalQuestionCount, questionCount);

        test.edit(title, questionCount, choiceCount, correctChoices, points, answerS3Keys,
            internalQuestionCount, year, month, week, opensAt, closesAt);
        deleteFilesAfterCommit(removed);
        return OnlineTestDetailResponse.from(test, answerFiles(test));
    }

    /** 공개하면 학생 목록에 잡힌다. 이미 공개된 테스트를 다시 호출해도 시각은 그대로다. */
    @Transactional
    public OnlineTestDetailResponse publish(Long testId) {
        OnlineTest test = findTest(testId);
        test.publish(OffsetDateTime.now());
        return OnlineTestDetailResponse.from(test, answerFiles(test));
    }

    /**
     * 제출이 1건이라도 있으면 409다. 응시 기록을 지우면 학생의 점수가 사라진다.
     *
     * <p>응시만 시작하고 제출은 안 한 행(IN_PROGRESS)은 함께 지운다.
     * 아직 아무 점수도 없어서 남길 이유가 없고, 남기면 FK로 삭제 자체가 막힌다.
     */
    @Transactional
    public void delete(Long testId) {
        OnlineTest test = findTest(testId);
        if (onlineTestSubmissionRepository.existsByOnlineTestIdAndStatus(
                testId, OnlineTestStatus.SUBMITTED)) {
            throw new BusinessException(ErrorCode.SUBMISSION_EXISTS);
        }
        onlineTestSubmissionRepository.deleteByOnlineTestId(testId);
        List<String> files = test.getAnswerS3Keys();
        onlineTestRepository.delete(test);
        deleteFilesAfterCommit(files);
    }

    /**
     * T-14 결과. <b>미응시 학생도 포함한다.</b>
     * average는 제출자만으로 계산하고 이 화면에만 있다 — 학생·학부모 응답에 넣지 마라.
     */
    @Transactional(readOnly = true)
    public OnlineTestResultsResponse results(Long testId) {
        OnlineTest test = findTest(testId);
        List<Student> students = enrollmentRepository.findActiveStudents(
            test.getClassRoom().getId(), LocalDate.now());
        Map<Long, OnlineTestSubmission> submissions =
            onlineTestSubmissionRepository.findByOnlineTestId(testId).stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));

        Short internalCount = test.getInternalQuestionCount();
        Short[] correct = test.getCorrectChoices();

        // 오프라인으로 본 학생. 온라인 테스트는 오프라인 테스트의 대체본이라
        // 반 전체가 종이로 본 주에는 아무도 온라인으로 내지 않는다
        Set<Long> offlineTaken = new HashSet<>(
            weeklyTestScoreRepository.findStudentIdsWithClinicScore(
                test.getClassRoom().getId(), test.getYear(), test.getMonth(), test.getWeek()));

        List<OnlineTestResultsResponse.Item> items = students.stream()
            .map(student -> {
                OnlineTestSubmission submission = submissions.get(student.getId());
                boolean submitted = submission != null && submission.isSubmitted();
                if (!submitted) {
                    // 실제로 온라인에 낸 것이 우선이다. 안 냈을 때만 성적 칸을 본다
                    OnlineTestTakeStatus status = submission == null
                        && offlineTaken.contains(student.getId())
                        ? OnlineTestTakeStatus.OFFLINE
                        : OnlineTestTakeStatus.of(submission);
                    return new OnlineTestResultsResponse.Item(
                        student.getId(), student.getName(), status,
                        null, null, null, null, List.of(), null);
                }
                Short[] chosen = submission.getChosenChoices();
                // 내부지문 문항 수가 없으면 집계하지 않는다. 0으로 채우면 "0개 맞음"으로 읽힌다
                Short internalCorrect = internalCount == null
                    ? null : countCorrect(correct, chosen, 0, internalCount);
                Short externalCorrect = internalCount == null
                    ? null : countCorrect(correct, chosen, internalCount, correct.length);
                return new OnlineTestResultsResponse.Item(
                    student.getId(), student.getName(),
                    OnlineTestTakeStatus.of(submission),
                    submission.getScore(), submission.getCorrectCount(),
                    internalCorrect, externalCorrect,
                    wrongQuestionNos(correct, chosen),
                    submission.getSubmittedAt());
            })
            .toList();

        int submitted = (int) items.stream()
            .filter(i -> i.status() == OnlineTestTakeStatus.SUBMITTED).count();
        int inProgress = (int) items.stream()
            .filter(i -> i.status() == OnlineTestTakeStatus.IN_PROGRESS).count();
        int offline = (int) items.stream()
            .filter(i -> i.status() == OnlineTestTakeStatus.OFFLINE).count();

        return new OnlineTestResultsResponse(
            new OnlineTestResultsResponse.Test(test.getId(), test.getTitle(),
                test.getQuestionCount(), internalCount, test.getClassRoom().getName(),
                clinicReflectionOf(test)),
            new OnlineTestResultsResponse.Counts(items.size(),
                items.size() - submitted - inProgress - offline,
                inProgress, submitted, offline),
            average(items),
            items);
    }

    /**
     * 이 테스트 결과가 성적 기입 탭의 클리닉 칸으로 자동 반영되는지.
     * 판정은 실제로 반영하는 {@code WeeklyTestService.reflectClinicScore}와 같은 곳에 있다 —
     * 여기서 다시 구현하면 "반영됨"이라고 떠 있는데 칸은 비어 있는 상태가 생긴다.
     */
    private ClinicReflection clinicReflectionOf(OnlineTest test) {
        Short internalCount = test.getInternalQuestionCount();
        return weeklyTestService.clinicReflectionOf(
            test.getClassRoom().getId(), test.getYear(), test.getMonth(), test.getWeek(),
            internalCount,
            internalCount == null ? null : (short) (test.getQuestionCount() - internalCount));
    }

    /** 해설지 업로드 URL. 서버는 바이트를 다루지 않고 클라이언트가 S3로 직접 PUT한다. */
    @Transactional(readOnly = true)
    public AnswerUploadUrlResponse issueAnswerUploadUrl(AnswerUploadUrlRequest request) {
        if (!answerKeys.isSupportedType(request.contentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.bytes() > MAX_ANSWER_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String s3Key = answerKeys.issue(currentTeacher().getId(), request.contentType(),
            LocalDate.now());
        return new AnswerUploadUrlResponse(
            presignedUrlProvider.uploadUrl(s3Key, request.contentType(), request.bytes()), s3Key);
    }

    // ---------- 내부 ----------

    /**
     * 선생님 전용. 학생 한 명의 문항별 정오.
     *
     * <p>온라인 테스트는 클리닉 테스트의 온라인 대체본이다. 선생님이 이 화면을 보고
     * 성적 기입 탭의 클리닉 칸에 직접 적는다. 성적 자동 반영은 없다.
     */
    @Transactional(readOnly = true)
    public OnlineTestStudentDetailResponse studentDetail(Long testId, Long studentId) {
        studentAccessGuard.requireAccessible(studentId);

        OnlineTest test = findTest(testId);
        OnlineTestSubmission submission = onlineTestSubmissionRepository
            .findByOnlineTestIdAndStudentId(testId, studentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Short[] correct = test.getCorrectChoices();
        Short[] chosen = submission.getChosenChoices();
        Short internalCount = test.getInternalQuestionCount();

        List<OnlineTestStudentDetailResponse.QuestionResult> results = new ArrayList<>();
        for (int i = 0; i < correct.length; i++) {
            Short picked = i < chosen.length ? chosen[i] : null;
            String section = internalCount == null
                ? null : (i < internalCount ? "INTERNAL" : "EXTERNAL");
            results.add(new OnlineTestStudentDetailResponse.QuestionResult(
                i + 1, picked, correct[i],
                picked != null && picked.equals(correct[i]), section));
        }

        return new OnlineTestStudentDetailResponse(studentId,
            submission.getStudent().getName(), submission.getCorrectCount(),
            test.getQuestionCount(), internalCount, List.copyOf(results));
    }

    /**
     * [fromIndex, toIndex) 구간의 정답 개수. chosen이 null이면 미체크라 오답이다.
     * 내부지문(0 ~ internalQuestionCount)과 외부지문(그 뒤)을 나눠 세는 데 쓴다.
     */
    static short countCorrect(Short[] correctChoices, Short[] chosenChoices,
                              int fromIndex, int toIndex) {
        short count = 0;
        for (int i = fromIndex; i < toIndex && i < correctChoices.length; i++) {
            Short chosen = i < chosenChoices.length ? chosenChoices[i] : null;
            if (chosen != null && chosen.equals(correctChoices[i])) {
                count++;
            }
        }
        return count;
    }

    /** 틀린 문항 번호. <b>1부터 센다</b> — 선생님이 시험지에서 찾는 번호와 같아야 한다. */
    static List<Integer> wrongQuestionNos(Short[] correctChoices, Short[] chosenChoices) {
        List<Integer> wrong = new ArrayList<>();
        for (int i = 0; i < correctChoices.length; i++) {
            Short chosen = i < chosenChoices.length ? chosenChoices[i] : null;
            if (chosen == null || !chosen.equals(correctChoices[i])) {
                wrong.add(i + 1);
            }
        }
        return List.copyOf(wrong);
    }

    /** 내부지문은 앞에서부터 세므로 전체 문항 수를 넘을 수 없다. */
    private void validateInternalCount(Short internalQuestionCount, short questionCount) {
        if (internalQuestionCount != null
            && (internalQuestionCount < 0 || internalQuestionCount > questionCount)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private OnlineTest findTest(Long testId) {
        return onlineTestRepository.findWithClassRoom(testId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Teacher currentTeacher() {
        return teacherRepository.findByUserId(CurrentUser.get().userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<OnlineTestDetailResponse.AnswerFile> answerFiles(OnlineTest test) {
        return test.getAnswerS3Keys().stream()
            .map(key -> new OnlineTestDetailResponse.AnswerFile(key,
                presignedUrlProvider.readUrl(key)))
            .toList();
    }

    /**
     * S3 객체는 되돌릴 수 없어서 커밋이 확정된 뒤에 지운다. 트랜잭션 안에서 지우면 뒤에서
     * 롤백돼도 파일은 이미 없다.
     */
    private void deleteFilesAfterCommit(List<String> s3Keys) {
        if (s3Keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            s3Keys.forEach(presignedUrlProvider::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                s3Keys.forEach(presignedUrlProvider::deleteQuietly);
            }
        });
    }

    private BigDecimal average(List<OnlineTestResultsResponse.Item> items) {
        List<BigDecimal> scores = items.stream()
            .map(OnlineTestResultsResponse.Item::score)
            .filter(score -> score != null)
            .toList();
        if (scores.isEmpty()) {
            return null;
        }
        return scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * 공개된 테스트에 채점에 영향을 주는 값을 보내면 409다.
     * 값이 같으면 통과시킨다 — 화면이 전체 폼을 그대로 다시 보내는 경우가 있다.
     */
    private void requireNoScoringChange(OnlineTest test, OnlineTestUpdateRequest request) {
        boolean changed =
            (request.questionCount() != null
                && !request.questionCount().equals(test.getQuestionCount()))
            || (request.choiceCount() != null
                && !request.choiceCount().equals(test.getChoiceCount()))
            || (request.correctChoices() != null
                && !java.util.Arrays.equals(request.correctChoices(), test.getCorrectChoices()))
            || (request.points() != null
                && !java.util.Arrays.equals(request.points(), test.getPoints()));

        if (changed) {
            throw new BusinessException(ErrorCode.TEST_ALREADY_PUBLISHED);
        }
    }

    /**
     * 정답 배열은 길이와 값 범위를 둘 다 본다. 25문항인데 24개만 넣으면
     * 전원의 점수가 조용히 틀리고, 0이나 6은 어떤 학생도 맞힐 수 없는 문항이 된다.
     */
    private void validateChoices(Short[] correctChoices, short questionCount, short choiceCount) {
        if (correctChoices == null || correctChoices.length != questionCount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        for (Short choice : correctChoices) {
            if (choice == null || choice < 1 || choice > choiceCount) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /**
     * 배점 배열도 길이 검사를 한다. 짧으면 뒤쪽 문항 배점이 없어 총점이 어긋나는데
     * 증상이 정답 배열 오류와 똑같아서 원인을 찾기 어렵다.
     */
    private void validatePoints(Short[] points, short questionCount) {
        if (points == null) {
            return;
        }
        if (points.length != questionCount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        for (Short point : points) {
            if (point == null || point < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /**
     * scoreType이 있으면 subject가 필수다. scores.subject가 NOT NULL이라
     * 과목 없이는 성적 반영 행을 만들 수 없다 (ck_online_tests_subject).
     *
     * <p><b>여기서 "영어"를 기본값으로 채우지 마라.</b> 성적 관리 범위가 미확정이다.
     */
    /**
     * 해설지 키 검증. 빈 칸은 버리고, 서명이 이 선생님 것인지 본다. 5장이 상한이다
     * (공지 첨부와 같다). 같은 키가 두 번 오면 한 번만 남긴다.
     */
    private List<String> validAnswerKeys(List<String> keys, Long teacherId) {
        List<String> clean = keys.stream()
            .filter(key -> key != null && !key.isBlank())
            .distinct()
            .toList();
        if (clean.size() > MAX_ANSWER_FILES) {
            throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
        }
        for (String key : clean) {
            if (!answerKeys.matches(key, teacherId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
        return clean;
    }

    private static List<String> single(String key) {
        return key == null ? List.of() : List.of(key);
    }

    /** DB CHECK가 1~100이다. */
    private short validQuestionCount(Short questionCount) {
        if (questionCount == null || questionCount < 1 || questionCount > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return questionCount;
    }

    /** DB CHECK가 2~10이다. */
    private short validChoiceCount(Short choiceCount) {
        if (choiceCount == null || choiceCount < 2 || choiceCount > 10) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return choiceCount;
    }

    private void validateWeek(Short year, Short month, Short week) {
        if (year == null || month == null || week == null
            || month < 1 || month > 12 || week < 1 || week > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validatePeriod(OffsetDateTime opensAt, OffsetDateTime closesAt) {
        if (opensAt != null && closesAt != null && closesAt.isBefore(opensAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
