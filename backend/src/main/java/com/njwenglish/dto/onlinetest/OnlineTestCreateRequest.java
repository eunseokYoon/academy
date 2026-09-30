package com.njwenglish.dto.onlinetest;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * T-14 출제. <b>문제지 파일은 받지 않는다.</b> 시험은 종이로 보고 서버가 가진 건
 * 정답 배열과 해설지(answerS3Key)뿐이다.
 *
 * <p>correctChoices·points 길이는 questionCount와 같아야 한다. 24개만 넣으면
 * 전원의 점수가 조용히 틀리므로 DB CHECK가 잡기 전에 서비스에서 400으로 막는다.
 *
 * <p>internalQuestionCount는 앞에서부터 몇 문항이 내부지문인지다. 비우면 내부·외부
 * 집계를 하지 않는다. 클리닉 테스트를 온라인으로 대체할 때 선생님이 결과를 옮겨 적기 쉽게 한다.
 */
public record OnlineTestCreateRequest(
    @NotNull Long classRoomId,
    @NotBlank String title,
    @NotNull Short questionCount,
    @NotNull Short choiceCount,
    @NotEmpty Short[] correctChoices,
    Short[] points,
    /** 옛 화면의 해설지 한 장. answerS3Keys 가 있으면 무시한다. 새 화면은 쓰지 마라. */
    String answerS3Key,
    /** 해설지 여러 장(2026-09-29, 최대 5). 올린 순서다. */
    List<String> answerS3Keys,
    /** 앞 N문항이 내부지문. null이면 내부·외부 집계를 하지 않는다. */
    @Min(0) Short internalQuestionCount,
    @NotNull Short year,
    @NotNull Short month,
    @NotNull Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt
) {
}
