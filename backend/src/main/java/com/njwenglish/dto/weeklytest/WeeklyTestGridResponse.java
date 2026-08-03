package com.njwenglish.dto.weeklytest;

import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.util.List;

/**
 * 그리드 한 장. <b>tests는 항상 4종을 순서대로</b> 내려준다 — 그 주에 안 본 시험도
 * 값이 null인 항목으로 들어간다. 프론트가 열을 고정으로 그린다.
 */
public record WeeklyTestGridResponse(
    Long classRoomId,
    short year,
    short month,
    short week,
    List<StudentRow> students,
    List<TestColumn> tests
) {
    /** enrolled=false는 지금은 재원생이 아니지만 그 주차에 성적이 남아 있는 학생이다. */
    public record StudentRow(Long studentId, String name, boolean enrolled) {
    }

    public record TestColumn(
        WeeklyTestType testType,
        Short totalCount,
        Short internalTotal,
        Short externalTotal,
        List<Cell> cells
    ) {
    }

    public record Cell(
        Long studentId,
        Short correctCount,
        Short internalCorrect,
        Short externalCorrect,
        TestResult result,
        boolean retestPassed
    ) {
    }
}
