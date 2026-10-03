package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.dto.weeklytest.ClinicReflection;
import com.njwenglish.dto.weeklytest.WeeklyTestGridResponse;
import com.njwenglish.dto.weeklytest.WeeklyTestSaveRequest;
import com.njwenglish.entity.ClassRoom;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.repository.ClassRoomRepository;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import com.njwenglish.service.push.PushEvent;
import com.njwenglish.service.push.PushTopic;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T-8. 반 × 주차 한 화면에서 4종을 전부 입력하고 언제든 수정한다.
 *
 * <p>종류를 하나 골라 한 종류씩 입력하는 방식으로 되돌리지 마라. 미팅에서 확인된
 * 실제 운영은 엑셀처럼 한 화면에서 다 채우는 것이다.
 */
@Service
@RequiredArgsConstructor
public class WeeklyTestService {

    private final WeeklyTestRepository weeklyTestRepository;
    private final WeeklyTestScoreRepository weeklyTestScoreRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassRoomRepository classRoomRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 그리드 한 장. 명단은 <b>재원생 ∪ 그 주차에 성적이 있는 학생</b>이다.
     *
     * <p>주차에는 날짜가 없어 findActiveStudents의 기준일을 만들 수 없다. 오늘 기준
     * 재원생만 뽑으면 지난 주차를 열었을 때 퇴원생 성적이 표에서 사라진다.
     */
    @Transactional(readOnly = true)
    public WeeklyTestGridResponse grid(Long classRoomId, short year, short month, short week) {
        List<WeeklyTestGridResponse.StudentRow> students =
            roster(classRoomId, year, month, week);

        List<WeeklyTest> tests = weeklyTestRepository
            .findByClassRoomIdAndYearAndMonthAndWeek(classRoomId, year, month, week);
        Map<WeeklyTestType, WeeklyTest> byType = tests.stream()
            .collect(Collectors.toMap(WeeklyTest::getTestType, Function.identity()));

        Map<Long, List<WeeklyTestScore>> cellsByTestId = tests.isEmpty()
            ? Map.of()
            : weeklyTestScoreRepository
                .findByWeeklyTestIdIn(tests.stream().map(WeeklyTest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(cell -> cell.getWeeklyTest().getId()));

        // 열은 항상 4개다. 그 주에 안 본 시험도 값이 null인 항목으로 들어간다
        List<WeeklyTestGridResponse.TestColumn> columns = new ArrayList<>();
        for (WeeklyTestType type : WeeklyTestType.values()) {
            WeeklyTest test = byType.get(type);
            if (test == null) {
                columns.add(new WeeklyTestGridResponse.TestColumn(
                    type, null, null, null, List.of()));
                continue;
            }
            List<WeeklyTestGridResponse.Cell> cells =
                cellsByTestId.getOrDefault(test.getId(), List.of()).stream()
                    .map(cell -> new WeeklyTestGridResponse.Cell(
                        cell.getStudent().getId(), cell.getCorrectCount(),
                        cell.getInternalCorrect(), cell.getExternalCorrect(),
                        cell.getResult(), cell.isRetestPassed()))
                    .toList();
            columns.add(new WeeklyTestGridResponse.TestColumn(type, test.getTotalCount(),
                test.getInternalTotal(), test.getExternalTotal(), cells));
        }

        // 저장 때 되돌려받아 "화면을 연 뒤에 생긴 칸"을 알아보는 데 쓴다
        return new WeeklyTestGridResponse(classRoomId, year, month, week,
            OffsetDateTime.now(), students, columns);
    }

    /** 재원생 ∪ 그 주차 성적 보유자. 재원생을 먼저 넣어 이름순을 유지한다. */
    private List<WeeklyTestGridResponse.StudentRow> roster(Long classRoomId, short year,
                                                           short month, short week) {
        Map<Long, WeeklyTestGridResponse.StudentRow> rows = new LinkedHashMap<>();
        for (Student student : enrollmentRepository
            .findActiveStudents(classRoomId, LocalDate.now())) {
            rows.put(student.getId(), new WeeklyTestGridResponse.StudentRow(
                student.getId(), student.getName(), true));
        }
        for (Student student : weeklyTestScoreRepository
            .findStudentsWithScores(classRoomId, year, month, week)) {
            rows.putIfAbsent(student.getId(), new WeeklyTestGridResponse.StudentRow(
                student.getId(), student.getName(), false));
        }
        return List.copyOf(rows.values());
    }

    /**
     * 그리드 한 장 저장. 요청에 들어온 종류·학생만 처리한다.
     *
     * <p><b>셀이 없는 헤더는 남겨둔다.</b> 선생님이 전체 문항 수만 먼저 적어두고 점수는
     * 나중에 채울 수 있어야 한다. 학생·학부모 조회는 셀 기준이라 빈 헤더가 있어도
     * 섹션이 뜨지 않는다.
     */
    @Transactional
    public void save(WeeklyTestSaveRequest request) {
        for (WeeklyTestSaveRequest.TestInput input : request.tests()) {
            validateHeader(input);
            input.cellsOrEmpty().forEach(cell -> validateCell(input, cell));
        }

        ClassRoom classRoom = classRoomRepository.findById(request.classRoomId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Set<Long> written = new HashSet<>();
        for (WeeklyTestSaveRequest.TestInput input : request.tests()) {
            saveColumn(classRoom, request, input, written);
        }
        // 푸시 #4. 새로 적었거나 값이 바뀐 칸의 학생만. 지운 칸은 알릴 것이 없다.
        // 그날 두 번째 저장부터는 PushPlanner 가 하루 1건으로 묶는다
        if (!written.isEmpty()) {
            eventPublisher.publishEvent(PushEvent.labeled(PushTopic.WEEKLY_SCORE, written,
                MonthWeeks.label(request.month(), request.week())));
        }
    }

    private void saveColumn(ClassRoom classRoom, WeeklyTestSaveRequest request,
                            WeeklyTestSaveRequest.TestInput input, Set<Long> written) {
        Optional<WeeklyTest> found = weeklyTestRepository
            .findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(classRoom.getId(),
                input.testType(), request.year(), request.month(), request.week());

        // REVIEW는 헤더에 채울 값이 없다. 셀이 하나라도 있을 때만 헤더를 만든다
        boolean wantsColumn = input.testType() == WeeklyTestType.REVIEW
            ? input.cellsOrEmpty().stream().anyMatch(cell -> !cell.isEmpty())
            : !input.hasNoHeader();

        if (!wantsColumn) {
            found.ifPresent(header -> deleteColumn(header, request.loadedAt()));
            return;
        }

        WeeklyTest test = found
            .map(existing -> {
                existing.changeTotals(input.totalCount(), input.internalTotal(),
                    input.externalTotal());
                return existing;
            })
            .orElseGet(() -> weeklyTestRepository.save(WeeklyTest.create(classRoom,
                input.testType(), request.year(), request.month(), request.week(),
                input.totalCount(), input.internalTotal(), input.externalTotal())));

        for (WeeklyTestSaveRequest.CellInput cell : input.cellsOrEmpty()) {
            if (saveCell(test, cell, request.loadedAt())) {
                written.add(cell.studentId());
            }
        }
    }

    /**
     * 헤더를 비워 보낸 열을 지운다. 헤더를 지우면 딸린 셀도 ON DELETE CASCADE로 함께 사라진다.
     *
     * <p><b>화면을 연 뒤에 생긴 칸이 있으면 헤더를 남긴다.</b> 선생님이 그리드를 열었을 때
     * 그 주 클리닉 헤더가 없었다면 화면의 헤더 칸은 비어 있다. 그 사이 학생이 온라인
     * 클리닉 테스트를 내면 {@link #reflectClinicScore}가 헤더와 칸을 만든다. 그대로 두면
     * 선생님이 단어 점수만 적고 저장해도 비어 있던 클리닉 헤더가 삭제로 읽혀 방금 반영된
     * 성적이 CASCADE로 사라진다. {@link #saveCell}의 loadedAt 검사와 같은 이유다 —
     * 그쪽은 칸 삭제만 지켰다. 이때는 화면에 보였던(loadedAt보다 오래된) 칸만 지운다.
     */
    private void deleteColumn(WeeklyTest header, OffsetDateTime loadedAt) {
        if (loadedAt != null) {
            List<WeeklyTestScore> scores = weeklyTestScoreRepository
                .findByWeeklyTestIdIn(List.of(header.getId()));
            boolean hasFresh = scores.stream()
                .anyMatch(score -> score.getUpdatedAt().isAfter(loadedAt));
            if (hasFresh) {
                scores.stream()
                    .filter(score -> !score.getUpdatedAt().isAfter(loadedAt))
                    .forEach(weeklyTestScoreRepository::delete);
                return;
            }
        }
        weeklyTestRepository.delete(header);
    }

    // ---------- 온라인 테스트 자동 반영 ----------

    /**
     * 온라인 클리닉 테스트를 낸 학생의 칸을 채운다. <b>학생이 제출하는 순간 불린다</b>
     * (2026-08-10 확정) — 선생님이 결과를 눈으로 읽어 옮겨 적던 것을 대신한다.
     *
     * <p>키가 겹쳐서 가능한 일이다. {@code online_tests}에 이미
     * {@code (class_room_id, year, month, week)}가 있어 주차를 날짜에서 추론하지 않는다.
     *
     * <p><b>세 경우에 조용히 건너뛴다.</b> 예외를 던지면 학생의 제출 자체가 실패한다 —
     * 성적 반영은 곁다리지 제출의 목적이 아니다. 대신 선생님이 결과 화면에서
     * 왜 안 됐는지 볼 수 있게 {@link #clinicReflectionOf}가 같은 판정을 다시 계산해 준다.
     *
     * <ol>
     *   <li>내부지문 문항 수가 없거나 한쪽이 0 — 클리닉 헤더는 내부·외부가 <b>둘 다 1 이상</b>
     *       이어야 한다({@code ck_weekly_tests_shape}). 0을 넣으면 DB가 막는다</li>
     *   <li>이미 있는 헤더와 문항 수가 다름 — 헤더는 <b>반 공통</b>이라 한 명 때문에 덮으면
     *       나머지 학생의 정답률이 통째로 어긋난다</li>
     *   <li>그 학생 칸이 이미 있음 — 선생님이 손으로 적은 값이 우선이다</li>
     * </ol>
     *
     * <p>studentId가 아니라 Student를 받는다. 호출부(제출)가 이미 requireSelf로 본인을
     * 확인한 뒤라 여기서 다시 검사할 대상이 없다.
     */
    @Transactional
    public void reflectClinicScore(ClassRoom classRoom, short year, short month, short week,
                                   Short internalTotal, Short externalTotal,
                                   Student student, short internalCorrect,
                                   short externalCorrect) {
        if (internalTotal == null || externalTotal == null
            || internalTotal <= 0 || externalTotal <= 0) {
            return;
        }

        Optional<WeeklyTest> found = weeklyTestRepository
            .findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
                classRoom.getId(), WeeklyTestType.CLINIC, year, month, week);
        if (found.isEmpty()) {
            // 같은 주차를 동시에 낸 학생이 먼저 만들었을 수 있다 — 충돌하면 넘어가고 다시 읽는다
            weeklyTestRepository.insertClinicHeaderIfAbsent(classRoom.getId(), year, month, week,
                internalTotal, externalTotal);
            found = weeklyTestRepository.findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
                classRoom.getId(), WeeklyTestType.CLINIC, year, month, week);
        }
        if (found.isEmpty() || !matchesTotals(found.get(), internalTotal, externalTotal)) {
            return;
        }
        WeeklyTest header = found.get();

        if (weeklyTestScoreRepository
            .findByWeeklyTestIdAndStudentId(header.getId(), student.getId()).isPresent()) {
            return;
        }
        // CLINIC은 correctCount·result를 쓰지 않는다. 내부·외부 맞힌 개수가 전부다
        weeklyTestScoreRepository.save(WeeklyTestScore.create(
            header, student, null, internalCorrect, externalCorrect, null, false));
    }

    /**
     * 자동 반영이 될 상태인가. 결과 화면이 선생님에게 이유를 보여주는 데 쓴다.
     * {@link #reflectClinicScore}의 앞 두 검사와 <b>같은 판정이어야 한다</b> —
     * 갈라지면 "반영됨"이라고 떠 있는데 칸은 비어 있는 상태가 된다.
     */
    @Transactional(readOnly = true)
    public ClinicReflection clinicReflectionOf(Long classRoomId, short year, short month,
                                               short week, Short internalTotal,
                                               Short externalTotal) {
        if (internalTotal == null || externalTotal == null
            || internalTotal <= 0 || externalTotal <= 0) {
            return ClinicReflection.NO_INTERNAL_SPLIT;
        }
        return weeklyTestRepository
            .findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(
                classRoomId, WeeklyTestType.CLINIC, year, month, week)
            .map(header -> matchesTotals(header, internalTotal, externalTotal)
                ? ClinicReflection.REFLECTED : ClinicReflection.TOTAL_MISMATCH)
            .orElse(ClinicReflection.REFLECTED);
    }

    private boolean matchesTotals(WeeklyTest header, Short internalTotal, Short externalTotal) {
        return internalTotal.equals(header.getInternalTotal())
            && externalTotal.equals(header.getExternalTotal());
    }

    /** @return 값이 새로 생겼거나 바뀌었으면 true(푸시 대상). 지웠거나 그대로면 false. */
    private boolean saveCell(WeeklyTest test, WeeklyTestSaveRequest.CellInput cell,
                             OffsetDateTime loadedAt) {
        // studentId를 받는 모든 서비스 메서드의 첫 줄은 requireAccessible이다
        Student student = studentAccessGuard.requireAccessible(cell.studentId());

        Optional<WeeklyTestScore> found = weeklyTestScoreRepository
            .findByWeeklyTestIdAndStudentId(test.getId(), cell.studentId());

        if (cell.isEmpty()) {
            /*
             * 화면을 연 뒤에 생긴 칸은 지우지 않는다. 온라인 클리닉 테스트는 학생이 내는
             * 순간 이 칸을 채우는데, 선생님 화면에는 그 전 상태(빈 칸)가 떠 있다.
             * 그대로 저장하면 방금 반영된 성적이 조용히 사라진다 — 선생님은 지운 줄도 모른다.
             *
             * 선생님이 실제로 지우려던 칸은 화면에 값이 보였을 테니 loadedAt보다 오래됐다.
             * 그래서 이 검사는 "안 본 것만 지키고" 의도한 삭제는 막지 않는다.
             */
            found.filter(score -> loadedAt == null
                    || !score.getUpdatedAt().isAfter(loadedAt))
                .ifPresent(weeklyTestScoreRepository::delete);
            return false;
        }

        if (found.isPresent()) {
            // 같은 칸을 다시 저장하는 건 오타 수정이라는 정상 흐름이다. 409를 던지지 않는다
            return found.get().rewrite(cell.correctCount(), cell.internalCorrect(),
                cell.externalCorrect(), cell.result(), cell.retestPassed());
        }

        weeklyTestScoreRepository.save(WeeklyTestScore.create(test, student,
            cell.correctCount(), cell.internalCorrect(), cell.externalCorrect(),
            cell.result(), cell.retestPassed()));
        return true;
    }

    /** 종류마다 필요한 헤더값이 다르다. ck_weekly_tests_shape가 DB에서도 막지만 400으로 먼저 잡는다. */
    private void validateHeader(WeeklyTestSaveRequest.TestInput input) {
        boolean hasCells = input.cellsOrEmpty().stream().anyMatch(cell -> !cell.isEmpty());
        switch (input.testType()) {
            case WORD, PRACTICE -> {
                if (input.internalTotal() != null || input.externalTotal() != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                // totalCount가 존재하는데 0 이하면 거부 (셀 유무와 무관)
                if (input.totalCount() != null && input.totalCount() <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                // totalCount가 없는데 셀이 있으면 거부
                if (input.totalCount() == null && hasCells) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }
            case REVIEW -> {
                if (!input.hasNoHeader()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }
            case CLINIC -> {
                if (input.totalCount() != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                // internalTotal/externalTotal이 존재하는데 0 이하면 거부
                if (input.internalTotal() != null && input.internalTotal() <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                if (input.externalTotal() != null && input.externalTotal() <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                // 둘 중 정확히 하나만 있으면 거부: 둘 다 있거나 둘 다 없어야 함
                boolean hasInternal = input.internalTotal() != null;
                boolean hasExternal = input.externalTotal() != null;
                if (hasInternal != hasExternal) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                // 셀이 있는데 헤더가 없으면 거부 (이제 hasInternal/hasExternal이 같음을 보증)
                if (hasCells && !hasInternal) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
            }
        }
    }

    /** 종류에 없는 필드가 오면 거부한다. 잘못된 열에 값이 들어가는 걸 막는다. */
    private void validateCell(WeeklyTestSaveRequest.TestInput input,
                              WeeklyTestSaveRequest.CellInput cell) {
        WeeklyTestType type = input.testType();

        if (!type.usesCount() && cell.correctCount() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!type.usesSections()
            && (cell.internalCorrect() != null || cell.externalCorrect() != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (!type.usesResult() && (cell.result() != null || cell.retestPassed())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 재시험 통과는 Fail을 받은 학생에게만 붙는다. ck_wts_retest와 같은 규칙이다
        if (cell.retestPassed() && cell.result() != TestResult.FAIL) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        // 셀 값 하한 검증: ck_wts_counts는 >= 0을 요구한다
        if (cell.correctCount() != null && cell.correctCount() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (cell.internalCorrect() != null && cell.internalCorrect() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (cell.externalCorrect() != null && cell.externalCorrect() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (cell.correctCount() != null && input.totalCount() != null
            && cell.correctCount() > input.totalCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (cell.internalCorrect() != null && input.internalTotal() != null
            && cell.internalCorrect() > input.internalTotal()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (cell.externalCorrect() != null && input.externalTotal() != null
            && cell.externalCorrect() > input.externalTotal()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
