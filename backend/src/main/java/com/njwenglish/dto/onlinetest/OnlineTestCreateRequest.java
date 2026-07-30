package com.njwenglish.dto.onlinetest;

import com.njwenglish.entity.enums.ScoreType;
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
 * <p>scoreType을 넣으면 subject가 필수다. scores.subject가 NOT NULL이라
 * 과목 없이는 성적 반영 행을 만들 수 없다. 화면에서 성적 반영을 켜면 과목 입력란이 나타나야 한다.
 */
public record OnlineTestCreateRequest(
    @NotNull Long classRoomId,
    @NotBlank String title,
    @NotNull Short questionCount,
    @NotNull Short choiceCount,
    @NotEmpty Short[] correctChoices,
    Short[] points,
    String answerS3Key,
    ScoreType scoreType,
    String subject,
    @NotNull Short year,
    @NotNull Short month,
    @NotNull Short week,
    OffsetDateTime opensAt,
    OffsetDateTime closesAt
) {
}
