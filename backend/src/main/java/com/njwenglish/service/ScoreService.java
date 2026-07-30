package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.score.ScoreBulkCreateRequest;
import com.njwenglish.dto.score.ScoreBulkCreateResponse;
import com.njwenglish.dto.score.ScoreChartResponse;
import com.njwenglish.dto.score.ScoreCreateRequest;
import com.njwenglish.dto.score.ScoreResponse;
import com.njwenglish.dto.score.ScoreUpdateRequest;
import com.njwenglish.entity.ExamSchedule;
import com.njwenglish.entity.Score;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.enums.ScoreType;
import com.njwenglish.repository.ExamScheduleRepository;
import com.njwenglish.repository.ScoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-8 성적 입력, S-7 · P-4 조회.
 *
 * <p><b>등수·백분위·반 평균 같은 상대 지표는 계산도 노출도 하지 않는다.</b> scores에 컬럼이
 * 없는 것도 의도된 설계다. 필요하면 memo에 텍스트로 적는다.
 *
 * <p>성적 예측·목표 등급 추천 같은 분석 기능도 범위 밖이다.
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final BigDecimal MAX_WORD_SCORE = new BigDecimal("100");

    private final ScoreRepository scoreRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional(readOnly = true)
    public List<ScoreResponse> teacherScores(Long studentId) {
        studentAccessGuard.requireAccessible(studentId);
        return scoreRepository.findByStudent(studentId).stream()
            .map(ScoreResponse::from)
            .toList();
    }

    @Transactional
    public ScoreResponse create(Long studentId, ScoreCreateRequest request) {
        Student student = studentAccessGuard.requireAccessible(studentId);
        validate(request.scoreType(), request.rawScore(), request.gradeLevel(),
            request.examScheduleId(), request.year(), request.month(), request.week());

        return ScoreResponse.from(upsert(student, request.scoreType(),
            findSchedule(request.examScheduleId()), request.examName().trim(),
            request.subject().trim(), request.rawScore(), request.gradeLevel(),
            request.examDate(), request.year(), request.month(), request.week(),
            request.memo()));
    }

    /**
     * 한 시험의 여러 학생을 한 번에. 같은 요청을 두 번 보내도 uq_scores 키로 갱신되므로
     * 저장 버튼을 두 번 눌러도 P-4 그래프에 점이 두 개 찍히지 않는다.
     */
    @Transactional
    public ScoreBulkCreateResponse bulkCreate(ScoreBulkCreateRequest request) {
        validateWeek(request.year(), request.month(), request.week());
        ExamSchedule schedule = findSchedule(request.examScheduleId());
        if (request.scoreType() != ScoreType.INTERNAL && schedule != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        String examName = request.examName().trim();
        String subject = request.subject().trim();

        int created = 0;
        for (ScoreBulkCreateRequest.Item item : request.scores()) {
            validateValues(request.scoreType(), item.rawScore(), item.gradeLevel());
            Student student = studentAccessGuard.requireAccessible(item.studentId());
            boolean isNew = scoreRepository
                .findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
                    student.getId(), request.scoreType(), subject, examName, request.examDate())
                .isEmpty();

            upsert(student, request.scoreType(), schedule, examName, subject,
                item.rawScore(), item.gradeLevel(), request.examDate(),
                request.year(), request.month(), request.week(), item.memo());
            if (isNew) {
                created++;
            }
        }
        return new ScoreBulkCreateResponse(created, request.scores().size() - created);
    }

    @Transactional
    public ScoreResponse update(Long scoreId, ScoreUpdateRequest request) {
        Score score = findScore(scoreId);

        BigDecimal rawScore = request.rawScore() == null ? score.getRawScore() : request.rawScore();
        Short gradeLevel = request.gradeLevel() == null
            ? score.getGradeLevel() : request.gradeLevel();
        short year = request.year() == null ? score.getYear() : request.year();
        short month = request.month() == null ? score.getMonth() : request.month();
        short week = request.week() == null ? score.getWeek() : request.week();
        Long examScheduleId = request.examScheduleId() == null
            ? (score.getExamSchedule() == null ? null : score.getExamSchedule().getId())
            : request.examScheduleId();

        validate(score.getScoreType(), rawScore, gradeLevel, examScheduleId, year, month, week);
        score.rewrite(findSchedule(examScheduleId), rawScore, gradeLevel, year, month, week,
            request.memo() == null ? score.getMemo() : request.memo());
        return ScoreResponse.from(score);
    }

    @Transactional
    public void delete(Long scoreId) {
        scoreRepository.delete(findScore(scoreId));
    }

    @Transactional(readOnly = true)
    public ScoreChartResponse myScores(ScoreType scoreType) {
        return chart(studentAccessGuard.requireSelf().getId(), scoreType);
    }

    /** P-4. 첫 줄이 requireAccessible이다 — 다른 학부모의 자녀면 403이다. */
    @Transactional(readOnly = true)
    public ScoreChartResponse childScores(Long studentId, ScoreType scoreType) {
        return chart(studentAccessGuard.requireAccessible(studentId).getId(), scoreType);
    }

    /**
     * 온라인 테스트 자동 반영용. 채점 결과를 scores에 남긴다.
     *
     * <p>subject는 <b>online_tests.subject를 그대로</b> 넘긴다. 코드에서 "영어"로 지어내지 마라 —
     * 성적 관리 범위가 학원장 미확정 사항이다.
     */
    @Transactional
    public void recordFromOnlineTest(Student student, ScoreType scoreType, String subject,
                                     String examName, BigDecimal rawScore, LocalDate examDate,
                                     short year, short month, short week) {
        upsert(student, scoreType, null, examName, subject, rawScore, null, examDate,
            year, month, week, null);
    }

    // ---------- 내부 ----------

    private ScoreChartResponse chart(Long studentId, ScoreType scoreType) {
        boolean wantWord = scoreType == null || scoreType == ScoreType.WORD;
        boolean wantInternal = scoreType == null || scoreType == ScoreType.INTERNAL;
        boolean wantMock = scoreType == null || scoreType == ScoreType.MOCK;

        return new ScoreChartResponse(
            new ScoreChartResponse.WordSeries("주차", wantWord
                ? scoreRepository.findWordSeries(studentId).stream()
                    .map(ScoreChartResponse.Point::from).toList()
                : List.of()),
            wantInternal ? items(studentId, ScoreType.INTERNAL) : List.of(),
            wantMock ? items(studentId, ScoreType.MOCK) : List.of());
    }

    private List<ScoreChartResponse.Item> items(Long studentId, ScoreType scoreType) {
        return scoreRepository.findByType(studentId, scoreType).stream()
            .map(ScoreChartResponse.Item::from)
            .toList();
    }

    /**
     * 같은 시험을 다시 저장하면 새 행이 아니라 갱신이다.
     * uq_scores가 DB에서도 막지만, 여기서 흡수해야 409가 아니라 "수정됨"으로 동작한다.
     */
    private Score upsert(Student student, ScoreType scoreType, ExamSchedule schedule,
                         String examName, String subject, BigDecimal rawScore, Short gradeLevel,
                         LocalDate examDate, short year, short month, short week, String memo) {
        return scoreRepository
            .findByStudentIdAndScoreTypeAndSubjectAndExamNameAndExamDate(
                student.getId(), scoreType, subject, examName, examDate)
            .map(existing -> {
                existing.rewrite(schedule, rawScore, gradeLevel, year, month, week, memo);
                return existing;
            })
            .orElseGet(() -> scoreRepository.save(Score.create(student, scoreType, schedule,
                examName, subject, rawScore, gradeLevel, examDate, year, month, week, memo)));
    }

    /**
     * scoreId는 간접 참조지만 그 성적이 속한 학생을 찾아 동일하게 검증한다.
     * 지금은 TEACHER만 호출하는 경로라 항상 통과하지만, 규칙에 예외를 두면 다음에 빠뜨린다.
     */
    private Score findScore(Long scoreId) {
        Score score = scoreRepository.findById(scoreId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        studentAccessGuard.requireAccessible(score.getStudent().getId());
        return score;
    }

    private ExamSchedule findSchedule(Long examScheduleId) {
        if (examScheduleId == null) {
            return null;
        }
        return examScheduleRepository.findById(examScheduleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validate(ScoreType scoreType, BigDecimal rawScore, Short gradeLevel,
                          Long examScheduleId, Short year, Short month, Short week) {
        validateWeek(year, month, week);
        validateValues(scoreType, rawScore, gradeLevel);
        // exam_schedules는 내신 시험 일정이다. 단어·모의에 붙으면 D-day와 성적이 어긋나 보인다
        if (scoreType != ScoreType.INTERNAL && examScheduleId != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * WORD는 100점 만점 환산값이 필수고 등급이 없다.
     * 원점수(25문항 중 20개 → 20)가 그대로 들어오면 세로축이 무너지는데 에러가 안 나므로,
     * 0~100 범위 검사가 마지막 방어선이다.
     */
    private void validateValues(ScoreType scoreType, BigDecimal rawScore, Short gradeLevel) {
        if (rawScore != null && rawScore.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (gradeLevel != null && (gradeLevel < 1 || gradeLevel > 9)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (scoreType == ScoreType.WORD) {
            if (rawScore == null || rawScore.compareTo(MAX_WORD_SCORE) > 0 || gradeLevel != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /** DB CHECK가 month 1~12, week 1~5다. 저장 시점에 터지지 않게 여기서 막는다. */
    private void validateWeek(Short year, Short month, Short week) {
        if (year == null || month == null || week == null
            || month < 1 || month > 12 || week < 1 || week > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
