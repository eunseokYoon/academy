package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import com.njwenglish.entity.enums.HomeworkKind;
import java.time.OffsetDateTime;

/**
 * T-7 상단. 선생님 화면이라 반 이름까지 보여준다.
 *
 * <p>dueAt은 GRID 열에서 null일 수 있다(재제출을 열기 전). 프론트는 kind로 분기해라.
 */
public record HomeworkBriefResponse(Long id, String title, String classRoomName,
                                    HomeworkKind kind, OffsetDateTime dueAt) {

    public static HomeworkBriefResponse from(Homework homework) {
        return new HomeworkBriefResponse(homework.getId(), homework.getTitle(),
            homework.getClassRoom().getName(), homework.getKind(), homework.getDueAt());
    }
}
