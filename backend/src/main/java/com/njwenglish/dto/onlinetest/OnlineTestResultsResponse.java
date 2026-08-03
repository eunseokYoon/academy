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
    /** internalQuestionCount가 null이면 내부·외부 집계를 하지 않는다. */
    public record Test(Long testId, String title, Short questionCount,
                       Short internalQuestionCount, String classRoomName) {
    }

    public record Counts(int total, int notStarted, int inProgress, int submitted) {
    }

    /**
     * NOT_STARTED는 아직 응시 화면을 열지도 않은 학생이다.
     *
     * <p>internalCorrect·externalCorrect는 test.internalQuestionCount가 null이거나
     * 미제출이면 둘 다 null이다. wrongQuestionNos는 1부터 센 문항 번호이고
     * 미제출이면 빈 배열이다 — null을 내려주면 프론트가 매번 null 검사를 해야 한다.
     */
    public record Item(
        Long studentId,
        String name,
        OnlineTestTakeStatus status,
        BigDecimal score,
        Short correctCount,
        Short internalCorrect,
        Short externalCorrect,
        List<Integer> wrongQuestionNos,
        OffsetDateTime submittedAt
    ) {
    }
}
