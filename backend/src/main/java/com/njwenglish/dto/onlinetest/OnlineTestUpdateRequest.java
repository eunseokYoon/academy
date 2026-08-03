package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.enums.ScoreType;
import java.time.OffsetDateTime;

/**
 * <b>공개 후에는 questionCount·correctChoices·points를 바꿀 수 없다</b> (409).
 * 이미 응시한 학생의 점수가 소급 변경되기 때문이다. 고치려면 삭제 후 재출제한다.
 *
 * <p>공개 후에도 제목·해설지·기간은 고칠 수 있다. 채점 결과에 영향이 없다.
 */
public record OnlineTestUpdateRequest(
    String title,
    Short questionCount,
    Short choiceCount,
    Short[] correctChoices,
    Short[] points,
    String answerS3Key,
    ScoreType scoreType,
    String subject,
    /** 앞 N문항이 내부지문. null이면 기존 값을 유지한다. */
    Short internalQuestionCount,
    Short year,
    Short month,
    Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt
) {
}
