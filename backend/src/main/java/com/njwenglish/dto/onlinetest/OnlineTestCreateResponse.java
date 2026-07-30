package com.njwenglish.dto.onlinetest;

/** targetCount는 지금 그 반 재원생 수다. 출제 직후 화면에 "20명 대상"으로 보여 준다. */
public record OnlineTestCreateResponse(Long testId, int targetCount, boolean published) {
}
