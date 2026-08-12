package com.njwenglish.dto.homework;

import java.time.OffsetDateTime;

/** targetCount는 이 열에서 다시 내야 하는 학생 수다(🔺·❌를 받은 사람). */
public record ResubmitOpenResponse(int targetCount, OffsetDateTime dueAt) {
}
