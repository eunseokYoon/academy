package com.njwenglish.dto.regularexam;

import com.njwenglish.entity.enums.RegularExamSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** rawScore가 null이면 그 칸의 행을 삭제한다. 요청에 없는 칸은 건드리지 않는다. */
public record RegularExamSaveRequest(
    @NotNull Long classRoomId,
    @NotNull Short year,
    @NotNull @Valid List<Item> scores
) {
    public record Item(
        @NotNull Long studentId,
        @NotNull RegularExamSlot examSlot,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rawScore
    ) {
    }
}
