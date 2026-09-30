package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.OnlineTest;
import java.time.OffsetDateTime;
import java.util.List;

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
    /** 첫 해설지. answerFiles[0] 과 같다 — 옛 화면이 읽는다(7-3). */
    String answerS3Key,
    String answerFileUrl,
    /** 해설지 전부(2026-09-29). 없으면 빈 배열이다. */
    List<AnswerFile> answerFiles,
    Short year,
    Short month,
    Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt,
    OffsetDateTime publishedAt
) {
    public record AnswerFile(String s3Key, String url) {
    }

    /** files 는 test 의 해설지 순서 그대로 url 을 붙인 것이다. */
    public static OnlineTestDetailResponse from(OnlineTest test, List<AnswerFile> files) {
        AnswerFile first = files.isEmpty() ? null : files.get(0);
        return new OnlineTestDetailResponse(
            test.getId(), test.getClassRoom().getId(), test.getClassRoom().getName(),
            test.getTitle(), test.getQuestionCount(), test.getChoiceCount(),
            test.getCorrectChoices(), test.getPoints(),
            first == null ? null : first.s3Key(), first == null ? null : first.url(),
            List.copyOf(files),
            test.getYear(), test.getMonth(), test.getWeek(),
            test.getOpensAt(), test.getClosesAt(), test.getPublishedAt());
    }
}
