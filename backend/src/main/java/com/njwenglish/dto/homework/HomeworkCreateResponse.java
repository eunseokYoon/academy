package com.njwenglish.dto.homework;

/** targetCount는 마감일 기준 재원생 수다. 미리 생성된 submissions 행 수와 같다. */
public record HomeworkCreateResponse(Long homeworkId, int targetCount) {
}
