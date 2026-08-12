package com.njwenglish.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * 대상은 반 전체다. 일부 학생만 지정하는 기능은 1차 범위 밖이라 studentIds가 없다.
 *
 * <p>lessonId는 선택이지만 연결해야 캘린더의 숙제 완료율이 계산된다.
 */
public record HomeworkCreateRequest(@NotNull Long classRoomId,
                                    Long lessonId,
                                    @NotBlank @Size(max = 200) String title,
                                    String description,
                                    @NotNull OffsetDateTime dueAt,
                                    Long templateId,
                                    boolean saveAsTemplate) {
}
