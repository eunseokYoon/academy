package com.njwenglish.dto.studentscore;

import com.njwenglish.entity.enums.ScoreChartKind;
import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.math.BigDecimal;
import java.util.List;

/**
 * S-7 · P-4. 학생과 학부모가 <b>같은 응답</b>을 쓴다.
 *
 * <p>정기고사는 여기 없다 — 선생님만 보기로 확정된 데이터다.
 * 등수·백분위·반 평균 같은 상대 지표도 없다.
 *
 * <p>items는 <b>year·month·week 오름차순</b>이다. 그래프가 그대로 쓰는 순서고
 * 목록은 프론트에서 뒤집어 그린다. <b>다시 정렬하지 마라.</b>
 */
public record StudentScoreResponse(
    List<RetestNotice> retestScheduled,
    List<Section> sections
) {
    /** 화면 맨 위 요약 박스. 최신 주차부터 내림차순이다. */
    public record RetestNotice(WeeklyTestType testType, String weekLabel, String label) {
    }

    /** 데이터가 있는 종류만 내려간다. 빈 섹션을 넣지 마라 — 안 보는 시험이 화면에 뜬다. */
    public record Section(
        WeeklyTestType testType,
        String label,
        /**
         * <b>쓰지 마라.</b> chartKind가 대신한다(2026-09-10).
         * 옛 화면 호환으로만 남았고 다음 배포에서 지운다.
         */
        boolean chart,
        /** 이 종류를 어떤 그래프로 그리는가. 화면에서 testType으로 다시 분기하지 마라. */
        ScoreChartKind chartKind,
        List<Item> items
    ) {
    }

    /**
     * 종류마다 채워지는 필드가 다르다. 단어·실전모고는 correctCount/totalCount,
     * 클리닉은 internal·external, 리뷰는 result만이다.
     */
    public record Item(
        short year,
        short month,
        short week,
        String weekLabel,
        Short correctCount,
        Short totalCount,
        /**
         * <b>항상 null이다</b>(2026-09-10). 환산 점수·정답률을 전 화면에서 없앤다.
         * 필드를 지우지 마라 — 배포가 backend → web 순서라 옛 화면이 undefined를
         * 받으면 "undefined%"를 그린다. 계산을 되살리려면 회의 결정을 먼저 뒤집어라.
         */
        BigDecimal accuracy,
        Short internalCorrect,
        Short internalTotal,
        Short externalCorrect,
        Short externalTotal,
        TestResult result,
        boolean retestPassed,
        boolean retestScheduled,
        /**
         * 미응시(2026-09-29). 그 주차에 반이 그 시험을 봤는데(다른 학생 성적이 있다)
         * 이 학생 칸만 비어 있다. 값 칸은 전부 null 이다 — 0점이 아니다. 그래프는 이 줄을
         * 건너뛰고 목록만 [미응시]를 그린다. 판정은 StudentScoreQueryService 한 곳이다.
         */
        boolean absent
    ) {
        /** 미응시 줄. 값을 채울 방법이 없게 여기서만 만든다. */
        public static Item absentAt(short year, short month, short week, String weekLabel) {
            return new Item(year, month, week, weekLabel, null, null, null,
                null, null, null, null, null, false, false, true);
        }
    }
}
