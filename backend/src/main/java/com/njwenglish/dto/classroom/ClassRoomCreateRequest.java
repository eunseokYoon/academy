package com.njwenglish.dto.classroom;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 필수는 name 하나다. 반에는 학교·학년·유형이 없다 — 필요한 정보는
 * 「A고 2학년 목요일반」처럼 이름에 넣는다.
 *
 * <p>joinCode는 요청에 없다. 선생님이 직접 정하면 「고2반」처럼 추측 가능한 값이 들어간다.
 *
 * <p>dayOfWeek: 1=월 ~ 7=일 (ISO-8601). 비워 두면 lessons/bulk를 쓸 수 없다.
 */
public record ClassRoomCreateRequest(
    @NotBlank String name,
    Short dayOfWeek,
    LocalTime startTime,
    LocalDate termStart,
    LocalDate termEnd,
    String memo
) {
}
