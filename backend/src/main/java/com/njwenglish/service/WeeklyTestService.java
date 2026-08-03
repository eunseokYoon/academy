package com.njwenglish.service;

import com.njwenglish.dto.weeklytest.WeeklyTestGridResponse;
import com.njwenglish.entity.Student;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
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
}
