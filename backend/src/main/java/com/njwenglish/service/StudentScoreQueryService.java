package com.njwenglish.service;

import com.njwenglish.common.security.StudentAccessGuard;
import com.njwenglish.dto.studentscore.StudentScoreResponse;
import com.njwenglish.entity.WeeklyTest;
import com.njwenglish.entity.WeeklyTestScore;
import com.njwenglish.entity.enums.ScoreChartKind;
import com.njwenglish.entity.enums.WeeklyTestType;
import com.njwenglish.common.util.MonthWeeks;
import com.njwenglish.entity.Enrollment;
import com.njwenglish.repository.EnrollmentRepository;
import com.njwenglish.repository.WeeklyTestRepository;
import com.njwenglish.repository.WeeklyTestScoreRepository;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WeeklyTestScoreRepository weeklyTestScoreRepository;
    private final WeeklyTestRepository weeklyTestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentAccessGuard studentAccessGuard;

    /** 학부모가 자녀 성적을 볼 때. 첫 줄이 requireAccessible이다. */
    @Transactional(readOnly = true)
    public StudentScoreResponse forStudent(Long studentId) {
        studentAccessGuard.requireAccessible(studentId);
        return build(weeklyTestScoreRepository.findByStudentOrderedByWeek(studentId),
            absentTests(studentId));
    }

    /** 학생 본인. 대상이 로그인 사용자로 고정된다. */
    @Transactional(readOnly = true)
    public StudentScoreResponse forMe() {
        Long studentId = studentAccessGuard.requireSelf().getId();
        return build(weeklyTestScoreRepository.findByStudentOrderedByWeek(studentId),
            absentTests(studentId));
    }

    /**
     * 미응시(2026-09-29 사용자 결정). 빈 칸은 원래 행이 없어 화면에 아무것도 안 떴다(7-1).
     * 그러면 학부모는 「시험을 안 본 것」과 「아직 안 적은 것」을 구분할 수 없다.
     *
     * <p>세 조건이 다 맞을 때만 미응시다. 하나라도 빼면 거짓 미응시가 뜬다.
     * <ol>
     *   <li>그 반에서 <b>다른 학생 성적이 있다</b> — 헤더만 미리 적어 둔 주차는 아니다</li>
     *   <li>그 주가 <b>이미 지났다</b> — 선생님이 입력하는 중인 이번 주에 먼저 뜨지 않게</li>
     *   <li>그 학생이 그 주에 <b>그 반에 다녔다</b> — 입반 전·퇴원 뒤 주차는 아니다</li>
     * </ol>
     * 온라인 테스트를 내면 클리닉 칸이 채워지므로(reflectClinicScore) 저절로 미응시에서 빠진다.
     */
    private List<WeeklyTest> absentTests(Long studentId) {
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(KST);
        List<Long> classRoomIds = enrollments.stream()
            .map(e -> e.getClassRoom().getId()).distinct().toList();
        return weeklyTestRepository.findTakenByOthersOnly(classRoomIds, studentId).stream()
            .filter(test -> {
                // 평년 2월 5주처럼 없는 주차에 성적 헤더가 있을 수 있다(화면이 1~5를 다 준다).
                // 날짜가 없으니 「지났다」·「다녔다」를 판정할 수 없어 미응시로 띄우지 않는다
                if (!MonthWeeks.exists(test.getYear(), test.getMonth(), test.getWeek())) {
                    return false;
                }
                LocalDate start = MonthWeeks.startOf(test.getYear(), test.getMonth(),
                    test.getWeek());
                LocalDate end = MonthWeeks.endOf(test.getYear(), test.getMonth(),
                    test.getWeek());
                return end.isBefore(today) && enrollments.stream().anyMatch(e ->
                    e.getClassRoom().getId().equals(test.getClassRoom().getId())
                        && !e.getJoinedAt().isAfter(end)
                        && (e.getLeftAt() == null || e.getLeftAt().isAfter(start)));
            })
            .toList();
    }

    /**
     * cells는 리포지토리가 이미 year·month·week 오름차순으로 준다. 미응시 줄은 그 사이에
     * 끼워 넣어야 해서 종류마다 한 번 정렬한다 — 그래프 가로축 순서 그대로다.
     */
    private StudentScoreResponse build(List<WeeklyTestScore> cells, List<WeeklyTest> absent) {
        Map<WeeklyTestType, List<WeeklyTestScore>> byType = new EnumMap<>(WeeklyTestType.class);
        for (WeeklyTestScore cell : cells) {
            byType.computeIfAbsent(cell.getWeeklyTest().getTestType(), key -> new ArrayList<>())
                .add(cell);
        }
        Map<WeeklyTestType, List<WeeklyTest>> absentByType = new EnumMap<>(WeeklyTestType.class);
        for (WeeklyTest test : absent) {
            absentByType.computeIfAbsent(test.getTestType(), key -> new ArrayList<>()).add(test);
        }

        // 섹션 순서는 단어 → 리뷰 → 실전모고 → 클리닉 고정. 데이터가 있는 종류만 넣는다.
        // 빈 섹션을 내려주면 그 반이 안 보는 시험이 학부모 화면에 뜬다
        List<StudentScoreResponse.Section> sections = new ArrayList<>();
        for (WeeklyTestType type : WeeklyTestType.values()) {
            List<StudentScoreResponse.Item> items = new ArrayList<>();
            byType.getOrDefault(type, List.of()).forEach(cell -> items.add(toItem(cell)));
            absentByType.getOrDefault(type, List.of()).forEach(test -> items.add(
                StudentScoreResponse.Item.absentAt(test.getYear(), test.getMonth(),
                    test.getWeek(), test.weekLabel())));
            if (items.isEmpty()) {
                continue;
            }
            items.sort(Comparator
                .comparingInt((StudentScoreResponse.Item i) -> i.year())
                .thenComparingInt(StudentScoreResponse.Item::month)
                .thenComparingInt(StudentScoreResponse.Item::week));
            sections.add(new StudentScoreResponse.Section(type, LABELS.get(type),
                type.usesCount(), chartKindOf(type), items));
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
            // 정답률은 2026-09-10에 없앤다. 환산 점수를 어느 화면에도 보이지 않기로 했다.
            // 필드를 지우지 않는 이유는 배포가 backend → web 순서라 옛 화면이
            // undefined를 받으면 "undefined%"를 그리기 때문이다. 다음 배포에서 지운다
            null,
            cell.getInternalCorrect(), test.getInternalTotal(),
            cell.getExternalCorrect(), test.getExternalTotal(),
            cell.getResult(), cell.isRetestPassed(), cell.isRetestScheduled(), false);
    }

    /**
     * 그래프 종류. 단어·리뷰는 그래프가 없다(2026-09-10 회의) — 단어는 "12/15" 텍스트,
     * 리뷰는 통과 배지뿐이다. 리뷰에 개수가 없는 이유는 ck_weekly_tests_shape가
     * REVIEW의 total_count를 NULL로 못 박고 있어서다.
     */
    private ScoreChartKind chartKindOf(WeeklyTestType type) {
        return switch (type) {
            case WORD, REVIEW -> ScoreChartKind.NONE;
            case PRACTICE -> ScoreChartKind.BAR;
            case CLINIC -> ScoreChartKind.SPLIT_BAR;
        };
    }
}
