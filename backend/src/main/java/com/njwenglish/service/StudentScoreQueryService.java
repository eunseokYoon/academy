package com.njwenglish.service;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-7 · P-4. 학생과 학부모가 같은 응답을 쓴다.
 *
 * <p>정기고사를 여기에 얹지 마라 — 선생님만 보기로 확정된 데이터다.
 * 등수·백분위·반 평균 같은 상대 지표도 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class StudentScoreQueryService {

    private static final Map<WeeklyTestType, String> LABELS = Map.of(
        WeeklyTestType.WORD, "단어 테스트",
        WeeklyTestType.REVIEW, "리뷰 테스트",
        WeeklyTestType.PRACTICE, "실전 모의고사",
        WeeklyTestType.CLINIC, "클리닉");

    private final WeeklyTestScoreRepository weeklyTestScoreRepository;
    private final StudentAccessGuard studentAccessGuard;

    /** 학부모가 자녀 성적을 볼 때. 첫 줄이 requireAccessible이다. */
    @Transactional(readOnly = true)
    public StudentScoreResponse forStudent(Long studentId) {
        studentAccessGuard.requireAccessible(studentId);
        return build(weeklyTestScoreRepository.findByStudentOrderedByWeek(studentId));
    }

    /** 학생 본인. 대상이 로그인 사용자로 고정된다. */
    @Transactional(readOnly = true)
    public StudentScoreResponse forMe() {
        Long studentId = studentAccessGuard.requireSelf().getId();
        return build(weeklyTestScoreRepository.findByStudentOrderedByWeek(studentId));
    }

    /**
     * cells는 리포지토리가 이미 year·month·week 오름차순으로 준다.
     * <b>다시 정렬하지 마라</b> — 그래프 가로축 순서 그대로다.
     */
    private StudentScoreResponse build(List<WeeklyTestScore> cells) {
        Map<WeeklyTestType, List<WeeklyTestScore>> byType = new EnumMap<>(WeeklyTestType.class);
        for (WeeklyTestScore cell : cells) {
            byType.computeIfAbsent(cell.getWeeklyTest().getTestType(), key -> new ArrayList<>())
                .add(cell);
        }

        // 섹션 순서는 단어 → 리뷰 → 실전모고 → 클리닉 고정. 데이터가 있는 종류만 넣는다.
        // 빈 섹션을 내려주면 그 반이 안 보는 시험이 학부모 화면에 뜬다
        List<StudentScoreResponse.Section> sections = new ArrayList<>();
        for (WeeklyTestType type : WeeklyTestType.values()) {
            List<WeeklyTestScore> typeCells = byType.get(type);
            if (typeCells == null || typeCells.isEmpty()) {
                continue;
            }
            sections.add(new StudentScoreResponse.Section(type, LABELS.get(type),
                type.usesCount(), typeCells.stream().map(this::toItem).toList()));
        }

        List<StudentScoreResponse.RetestNotice> notices = cells.stream()
            .filter(WeeklyTestScore::isRetestScheduled)
            .sorted(Comparator
                .comparing((WeeklyTestScore cell) -> cell.getWeeklyTest().getYear())
                .thenComparing(cell -> cell.getWeeklyTest().getMonth())
                .thenComparing(cell -> cell.getWeeklyTest().getWeek())
                .reversed())
            .map(cell -> {
                WeeklyTest test = cell.getWeeklyTest();
                return new StudentScoreResponse.RetestNotice(test.getTestType(),
                    test.weekLabel(), test.weekLabel() + " " + LABELS.get(test.getTestType()));
            })
            .toList();

        return new StudentScoreResponse(notices, sections);
    }

    private StudentScoreResponse.Item toItem(WeeklyTestScore cell) {
        WeeklyTest test = cell.getWeeklyTest();
        return new StudentScoreResponse.Item(
            test.getYear(), test.getMonth(), test.getWeek(), test.weekLabel(),
            cell.getCorrectCount(), test.getTotalCount(),
            accuracy(cell.getCorrectCount(), test.getTotalCount()),
            cell.getInternalCorrect(), test.getInternalTotal(),
            cell.getExternalCorrect(), test.getExternalTotal(),
            cell.getResult(), cell.isRetestPassed(), cell.isRetestScheduled());
    }

    /**
     * 맞힌 개수 / 전체 문항 수 × 100, 소수 첫째 자리.
     *
     * <p>환산 점수를 저장하지 않으므로 조회 시점에 계산한다 — 선생님이 나중에
     * 전체 문항 수를 고쳐도 정답률이 따라온다.
     */
    private BigDecimal accuracy(Short correctCount, Short totalCount) {
        if (correctCount == null || totalCount == null || totalCount == 0) {
            return null;
        }
        return BigDecimal.valueOf(correctCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalCount), 1, RoundingMode.HALF_UP);
    }
}
