package com.njwenglish.dto.weeklytest;

import com.njwenglish.entity.enums.TestResult;
import com.njwenglish.entity.enums.WeeklyTestType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 그리드 한 장. <b>tests는 항상 4종을 순서대로</b> 내려준다 — 그 주에 안 본 시험도
 * 값이 null인 항목으로 들어간다. 프론트가 열을 고정으로 그린다.
 *
 * <p>loadedAt은 <b>이 응답을 만든 서버 시각</b>이다. 저장할 때 그대로 돌려보내면
 * 서버가 "화면을 연 뒤에 생긴 칸"을 알아보고 지우지 않는다. 클라이언트 시계를 쓰면
 * 기기마다 어긋나므로 서버가 만든 값을 왕복시킨다.
 */
public record WeeklyTestGridResponse(
    Long classRoomId,
    short year,
    short month,
    short week,
    OffsetDateTime loadedAt,
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
