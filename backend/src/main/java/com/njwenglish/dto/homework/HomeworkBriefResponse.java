package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import java.time.OffsetDateTime;

/** T-7 상단. 선생님 화면이라 반 이름까지 보여준다. */
public record HomeworkBriefResponse(Long id, String title, String classRoomName,
                                    OffsetDateTime dueAt) {

    public static HomeworkBriefResponse from(Homework homework) {
        return new HomeworkBriefResponse(homework.getId(), homework.getTitle(),
            homework.getClassRoom().getName(), homework.getDueAt());
    }
}
