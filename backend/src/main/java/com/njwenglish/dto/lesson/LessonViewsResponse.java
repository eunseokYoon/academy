package com.njwenglish.dto.lesson;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 선생님이 보려는 건 "누가 안 봤는지"다. 미시청 학생도 viewed=false로 반드시 포함한다.
 *
 * <p>이름은 students.name이다. 미가입 학생도 명단에 있어야 한다.
 */
public record LessonViewsResponse(
    Long lessonId,
    int totalStudents,
    int viewedCount,
    List<Item> items
) {
    public record Item(
        Long studentId,
        String name,
        boolean viewed,
        OffsetDateTime firstViewedAt,
        int watchSeconds
    ) {
    }
}
