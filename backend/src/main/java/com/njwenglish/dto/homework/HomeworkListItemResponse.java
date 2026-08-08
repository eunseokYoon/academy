package com.njwenglish.dto.homework;

import com.njwenglish.entity.Homework;
import com.njwenglish.entity.enums.HomeworkKind;
import java.time.OffsetDateTime;

/** lessonId가 null이면 캘린더 색띠 계산에서 빠진다. 목록에서 눈에 띄어야 연결을 유도할 수 있다. */
public record HomeworkListItemResponse(Long homeworkId,
                                       Long classRoomId,
                                       String classRoomName,
                                       Long lessonId,
                                       String title,
                                       HomeworkKind kind,
                                       OffsetDateTime dueAt,
                                       HomeworkCountsResponse counts) {

    /**
     * kind를 빼지 마라. 프론트가 이 값으로 GRID 열을 그리드 화면으로 보내고 뱃지를 고른다.
     * 없으면 재제출을 연 GRID 열(due_at이 채워져 목록 기간 필터를 통과한다)이 "온라인"으로
     * 표시되고 상세 화면으로 가서, 거기 수정 모달이 마감을 PATCH해 400을 받는다.
     */
    public static HomeworkListItemResponse of(Homework homework, HomeworkCountsResponse counts) {
        return new HomeworkListItemResponse(
            homework.getId(), homework.getClassRoom().getId(), homework.getClassRoom().getName(),
            homework.getLesson() == null ? null : homework.getLesson().getId(),
            homework.getTitle(), homework.getKind(), homework.getDueAt(), counts);
    }
}
