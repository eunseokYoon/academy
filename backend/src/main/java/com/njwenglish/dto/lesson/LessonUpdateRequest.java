package com.njwenglish.dto.lesson;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 보낸 필드만 바꾼다. published_at은 건드리지 않는다 — 공개는 POST /publish로만 이루어진다.
 * 그래야 작성 중인 초안이 학부모에게 새지 않는다.
 *
 * <p><b>videos는 통째로 교체다.</b> null이면 그대로 두고, 빈 배열이면 전부 지운다 —
 * 이 둘을 같게 다루면 영상을 지울 방법이 없어진다.
 */
public record LessonUpdateRequest(
    Short year,
    Short month,
    Short week,
    String title,
    @Valid List<LessonVideoRequest> videos,
    String content,
    String keyPoints,
    String homeworkNote,
    String clinicNote
) {
}
