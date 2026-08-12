package com.njwenglish.dto.lesson;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * year·month·week는 필수다. T-4 화면이 주차를 먼저 고르는 구조라 선택한 값을 그대로 보낸다.
 * 서버가 날짜에서 계산해 덮어쓰지 마라 — 달 경계에 걸친 주는 세는 방식이 갈린다.
 *
 * <p>videoUrl은 YouTube 링크 문자열만 저장한다. 세 가지 형식을 받아 videoId를 파싱한다.
 */
public record LessonCreateRequest(
    @NotNull Long classRoomId,
    @NotNull LocalDate lessonDate,
    @NotNull Short year,
    @NotNull Short month,
    @NotNull Short week,
    String title,
    String videoUrl,
    String content,
    String keyPoints,
    String nextPreview
) {
}
