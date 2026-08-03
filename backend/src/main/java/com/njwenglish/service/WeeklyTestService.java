package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
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
import com.njwenglish.repository.StudentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
    private final StudentRepository studentRepository;

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

        return new WeeklyTestGridResponse(classRoomId, year, month, week, students, columns);
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

        for (WeeklyTestSaveRequest.TestInput input : request.tests()) {
            saveColumn(classRoom, request, input);
        }
    }

    private void saveColumn(ClassRoom classRoom, WeeklyTestSaveRequest request,
                            WeeklyTestSaveRequest.TestInput input) {
        Optional<WeeklyTest> found = weeklyTestRepository
            .findByClassRoomIdAndTestTypeAndYearAndMonthAndWeek(classRoom.getId(),
                input.testType(), request.year(), request.month(), request.week());

        // REVIEW는 헤더에 채울 값이 없다. 셀이 하나라도 있을 때만 헤더를 만든다
        boolean wantsColumn = input.testType() == WeeklyTestType.REVIEW
            ? input.cellsOrEmpty().stream().anyMatch(cell -> !cell.isEmpty())
            : !input.hasNoHeader();

        if (!wantsColumn) {
            // 헤더를 지우면 딸린 셀도 ON DELETE CASCADE로 함께 사라진다
            found.ifPresent(weeklyTestRepository::delete);
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
            saveCell(test, cell);
        }
    }

    private void saveCell(WeeklyTest test, WeeklyTestSaveRequest.CellInput cell) {
        Optional<WeeklyTestScore> found = weeklyTestScoreRepository
            .findByWeeklyTestIdAndStudentId(test.getId(), cell.studentId());

        if (cell.isEmpty()) {
            found.ifPresent(weeklyTestScoreRepository::delete);
            return;
        }

        if (found.isPresent()) {
            // 같은 칸을 다시 저장하는 건 오타 수정이라는 정상 흐름이다. 409를 던지지 않는다
            found.get().rewrite(cell.correctCount(), cell.internalCorrect(),
                cell.externalCorrect(), cell.result(), cell.retestPassed());
            return;
        }

        Student student = studentRepository.findById(cell.studentId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        weeklyTestScoreRepository.save(WeeklyTestScore.create(test, student,
            cell.correctCount(), cell.internalCorrect(), cell.externalCorrect(),
            cell.result(), cell.retestPassed()));
    }

    /** 종류마다 필요한 헤더값이 다르다. ck_weekly_tests_shape가 DB에서도 막지만 400으로 먼저 잡는다. */
    private void validateHeader(WeeklyTestSaveRequest.TestInput input) {
        boolean hasCells = input.cellsOrEmpty().stream().anyMatch(cell -> !cell.isEmpty());
        switch (input.testType()) {
            case WORD, PRACTICE -> {
                if (input.internalTotal() != null || input.externalTotal() != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED);
                }
                if (hasCells && (input.totalCount() == null || input.totalCount() <= 0)) {
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
                if (hasCells && (input.internalTotal() == null || input.internalTotal() <= 0
                    || input.externalTotal() == null || input.externalTotal() <= 0)) {
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
