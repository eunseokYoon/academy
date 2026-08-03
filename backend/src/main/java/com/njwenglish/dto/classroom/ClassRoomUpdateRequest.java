package com.njwenglish.dto.classroom;

import java.util.List;

/**
 * 보낸 필드만 바꾼다.
 *
 * <p>schedules: null이면 스케줄을 건드리지 않는다. 빈 배열이면 전부 지운다(요일 미정).
 * 값이 있으면 통째 교체다.
 *
 * <p>이름 변경은 과거 기록에도 소급 적용된다. lessons·attendances가 class_room_id를
 * 참조하므로 지난 수업의 반 이름 표기도 함께 바뀐다. 의도된 동작이다.
 */
public record ClassRoomUpdateRequest(
    String name,
    List<ClassRoomScheduleDto> schedules,
    String memo
) {
}
