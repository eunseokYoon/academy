package com.njwenglish.dto.onlinetest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * <b>제출 후에만</b> 내려간다. 정답과 해설지 URL이 여기 처음 등장한다.
 *
 * <p>반 평균은 넣지 않는다. 상대 지표는 학생·학부모에게 노출 금지다.
 */
public record OnlineTestResultResponse(
    Long testId,
    String title,
    /**
     * 100점 환산. <b>화면에서 쓰지 마라</b>(2026-09-10 회의) — 환산 점수를 보이지 않기로 했다.
     * 값은 DB에 이미 저장돼 있어서 그대로 내려온다. 필드를 지우면 옛 화면이
     * "undefined점"을 그린다.
     */
    BigDecimal score,
    Short correctCount,
    Short questionCount,
    /** 앞 N문항이 내부지문. null이면 나누지 않은 테스트다. */
    Short internalQuestionCount,
    /** internalQuestionCount가 null이면 null이다. <b>0으로 채우지 마라.</b> */
    Short internalCorrect,
    /** internalQuestionCount가 null이면 null이다. */
    Short externalCorrect,
    OffsetDateTime submittedAt,
    /** 첫 해설지. answerFileUrls[0] 과 같다 — 옛 화면이 읽는다(7-3). */
    String answerFileUrl,
    /** 해설지 전부(2026-09-29). 없으면 빈 배열이다. */
    List<String> answerFileUrls,
    List<QuestionResult> results
) {
    /** chosen이 null이면 미체크다. 오답으로 처리되고 감점은 없다. */
    public record QuestionResult(int questionNo, Short chosen, Short correct, boolean isCorrect) {
    }
}
