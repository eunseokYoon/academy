package com.njwenglish.dto.classroom;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 필수는 name 하나다. 반에는 학교·학년·유형이 없다 — 필요한 정보는
 * 「A고 2학년 화목반」처럼 이름에 넣는다.
 *
 * <p>joinCode는 요청에 없다. 선생님이 직접 정하면 「고2반」처럼 추측 가능한 값이 들어간다.
 *
 * <p>schedules를 생략하거나 null이면 슬롯 0개다. 그 반에는 lessons/bulk를 쓸 수 없다.
 */
public record ClassRoomCreateRequest(
    @NotBlank String name,
    List<ClassRoomScheduleDto> schedules,
    String memo
) {
}
