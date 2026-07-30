package com.njwenglish.dto.onlinetest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * T-14 결과. 응시하지 않은 학생도 포함한다 — 선생님이 보려는 건 "누가 안 봤는지"다.
 *
 * <p><b>average는 선생님 화면에만 있다.</b> 학생·학부모 응답에 반 평균을 넣지 마라.
 * 제출자만으로 계산하고, 제출이 없으면 null이다.
 */
public record OnlineTestResultsResponse(
    Test test,
    Counts counts,
    BigDecimal average,
    List<Item> items
) {
    public record Test(Long testId, String title, Short questionCount, String classRoomName) {
    }

    public record Counts(int total, int notStarted, int inProgress, int submitted) {
    }

    /** NOT_STARTED는 아직 응시 화면을 열지도 않은 학생이다. */
    public record Item(
        Long studentId,
        String name,
        OnlineTestTakeStatus status,
        BigDecimal score,
        Short correctCount,
        OffsetDateTime submittedAt
    ) {
    }
}
