package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.OnlineTest;
import com.njwenglish.entity.enums.ScoreType;
import java.time.OffsetDateTime;

/**
 * <b>선생님 전용이다.</b> 정답과 해설지가 들어 있다.
 * 이 DTO를 학생 경로에 재사용하지 마라 — 응시 화면은 OnlineTestTakeResponse다.
 */
public record OnlineTestDetailResponse(
    Long testId,
    Long classRoomId,
    String classRoomName,
    String title,
    Short questionCount,
    Short choiceCount,
    Short[] correctChoices,
    Short[] points,
    String answerS3Key,
    String answerFileUrl,
    ScoreType scoreType,
    String subject,
    Short year,
    Short month,
    Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt,
    OffsetDateTime publishedAt
) {
    public static OnlineTestDetailResponse from(OnlineTest test, String answerFileUrl) {
        return new OnlineTestDetailResponse(
            test.getId(), test.getClassRoom().getId(), test.getClassRoom().getName(),
            test.getTitle(), test.getQuestionCount(), test.getChoiceCount(),
            test.getCorrectChoices(), test.getPoints(),
            test.getAnswerS3Key(), answerFileUrl,
            test.getScoreType(), test.getSubject(),
            test.getYear(), test.getMonth(), test.getWeek(),
            test.getOpensAt(), test.getClosesAt(), test.getPublishedAt());
    }
}
