package com.njwenglish.dto.studentscore;

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
        boolean chart,
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
        BigDecimal accuracy,
        Short internalCorrect,
        Short internalTotal,
        Short externalCorrect,
        Short externalTotal,
        TestResult result,
        boolean retestPassed,
        boolean retestScheduled
    ) {
    }
}
