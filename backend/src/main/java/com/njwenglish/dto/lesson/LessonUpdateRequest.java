package com.njwenglish.dto.lesson;

/**
 * 보낸 필드만 바꾼다. published_at은 건드리지 않는다 — 공개는 POST /publish로만 이루어진다.
 * 그래야 작성 중인 초안이 학부모에게 새지 않는다.
 */
public record LessonUpdateRequest(
    Short year,
    Short month,
    Short week,
    String title,
    String videoUrl,
    String content,
    String keyPoints,
    String nextPreview
) {
}
