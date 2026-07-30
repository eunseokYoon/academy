package com.njwenglish.dto.score;

/**
 * created는 새로 만든 행, updated는 이미 있어서 덮어쓴 행이다.
 * 저장 버튼을 두 번 눌러도 행이 늘지 않는다는 걸 화면에서 확인할 수 있어야 한다.
 */
public record ScoreBulkCreateResponse(int created, int updated) {
}
