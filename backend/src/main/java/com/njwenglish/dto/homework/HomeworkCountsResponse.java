package com.njwenglish.dto.homework;

/** T-6 목록과 T-7 상단에 함께 쓴다. total은 출제 대상 수다. */
public record HomeworkCountsResponse(int total, int notSubmitted, int submitted, int checked) {

    public static HomeworkCountsResponse empty(int total) {
        return new HomeworkCountsResponse(total, total, 0, 0);
    }
}
