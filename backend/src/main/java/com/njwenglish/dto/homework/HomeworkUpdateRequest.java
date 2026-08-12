package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * dueAt은 <b>늦추는 방향만</b> 허용한다. 앞당기면 이미 제출한 학생의 is_late를
 * 전부 재계산해야 한다. 앞당기려면 삭제 후 재출제한다.
 */
public record HomeworkUpdateRequest(@NotBlank @Size(max = 200) String title,
                                    String description,
                                    Long lessonId,
                                    OffsetDateTime dueAt) {
}
