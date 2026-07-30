package com.njwenglish.dto.lesson;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 재생 시작은 0, 이후 30초마다 누적 구간을 보낸다. 매초 호출하지 마라.
 *
 * <p>상한은 한 번의 호출이 담을 수 있는 최대 구간(1시간)이다. 클라이언트가 보낸 값을
 * 그대로 더하는 구조라 상한이 없으면 한 번의 요청으로 시청 시간이 임의로 부풀려진다.
 */
public record LessonViewRequest(
    @NotNull @Min(0) @Max(3600) Integer watchSeconds
) {
}
