package com.njwenglish.dto.onlinetest;

import java.time.OffsetDateTime;

/**
 * S-10 응시 화면. <b>정답(correctChoices)과 해설지 URL 필드가 여기 없다.</b>
 *
 * <p>null로 비우는 것도 안 된다. 응답에 들어가는 순간 브라우저 개발자 도구에서 그대로 보인다.
 * 결과용은 OnlineTestResultResponse로 분리돼 있으니 편의를 이유로 여기에 필드를 합치지 마라.
 *
 * <p>문제지 파일도 없다. 학생은 수업에서 받은 종이 시험지를 풀고 답만 입력한다.
 * 화면은 questionCount만큼 1~choiceCount 라디오 버튼 줄을 그리면 된다.
 */
public record OnlineTestTakeResponse(
    Long testId,
    String title,
    String classRoomName,
    Short questionCount,
    Short choiceCount,
    OffsetDateTime closesAt,
    Short[] chosenChoices,
    OnlineTestTakeStatus status
) {
}
