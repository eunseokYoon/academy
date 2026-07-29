package com.njwenglish.dto.classroom;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 보낸 필드만 바꾼다.
 *
 * <p>이름 변경은 과거 기록에도 소급 적용된다. lessons·attendances가 class_room_id를
 * 참조하므로 지난 수업의 반 이름 표기도 함께 바뀐다. 의도된 동작이다.
 */
public record ClassRoomUpdateRequest(
    String name,
    Short dayOfWeek,
    LocalTime startTime,
    LocalDate termStart,
    LocalDate termEnd,
    String memo
) {
}
