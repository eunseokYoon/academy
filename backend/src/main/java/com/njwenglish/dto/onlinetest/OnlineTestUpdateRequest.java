package com.njwenglish.dto.onlinetest;

import java.util.List;

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
    /** 옛 화면의 해설지 한 장(새로 올렸을 때만 온다). answerS3Keys 가 있으면 무시한다. */
    String answerS3Key,
    /**
     * 해설지 여러 장(2026-09-29). <b>null 이면 그대로, 빈 배열이면 전부 지운다</b> — 둘을 같게
     * 다루면 해설지를 지울 방법이 없어진다(수업 영상의 videos 와 같은 규칙).
     */
    List<String> answerS3Keys,
    /** 앞 N문항이 내부지문. null이면 기존 값을 유지한다. */
    Short internalQuestionCount,
    Short year,
    Short month,
    Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt
) {
}
