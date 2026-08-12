package com.njwenglish.dto.score;

import com.njwenglish.entity.enums.ExamType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 같은 시험이라도 <b>반마다 따로 등록한다.</b> 학교·학년 개념이 없어 한 번에 묶을 수 없다.
 * 반이 3개면 3번 호출한다 — T-11 화면에서 반을 다중 선택해 한 번의 저장으로 보내라.
 * 한 반이 빠지면 그 반 학생들의 D-day가 통째로 비어 있게 된다.
 */
public record ExamScheduleCreateRequest(
    @NotNull Long classRoomId,
    @NotNull Short year,
    @NotNull Short semester,
    @NotNull ExamType examType,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    String scopeNote
) {
}
