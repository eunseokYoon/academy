package com.njwenglish.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 재생 보고(2026-09-29). 앱·웹의 플레이어가 10~15초마다, 멈춤·끝·떠날 때 보낸다.
 *
 * @param embedUrl        서버가 준 그 영상의 embedUrl 그대로. 수업이 고쳐져 없어졌으면 버린다
 * @param durationSeconds 플레이어가 잰 영상 길이
 * @param buckets         지난 보고 뒤로 재생 위치가 지나간 10초 칸 번호(0부터)
 */
public record LessonWatchRequest(
    @NotBlank @Size(max = 1000) String embedUrl,
    @NotNull @Positive Double durationSeconds,
    @NotNull @Size(max = 2160) List<Integer> buckets
) {
}
