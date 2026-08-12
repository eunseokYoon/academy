package com.njwenglish.dto.onlinetest;

import java.time.OffsetDateTime;

/**
 * S-10 목록. remainingMinutes는 <b>서버가 계산</b>한다 — 클라이언트 시계는 틀릴 수 있다.
 * 음수면 마감 경과, null이면 마감이 없는 테스트다.
 *
 * <p>여기에도 정답·해설지는 없다.
 */
public record StudentOnlineTestListItemResponse(
    Long testId,
    String title,
    String classRoomName,
    Short questionCount,
    OffsetDateTime closesAt,
    Long remainingMinutes,
    OnlineTestTakeStatus status,
    int answeredCount
) {
}
