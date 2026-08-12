package com.njwenglish.dto.homework;

/**
 * T-6 목록과 T-7 상단에 함께 쓴다. total은 출제 대상 수다.
 *
 * <p>"확인 완료" 칸은 없다 — 선생님이 확인하는 단계 자체가 없어졌다. GRID에서 낸 것은
 * 곧바로 ⭕이고, 남은 구분은 냈는지 아닌지뿐이다.
 */
public record HomeworkCountsResponse(int total, int notSubmitted, int submitted) {

    public static HomeworkCountsResponse empty(int total) {
        return new HomeworkCountsResponse(total, total, 0);
    }
}
