package com.njwenglish.dto.regularexam;

import com.njwenglish.entity.enums.RegularExamSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * <b>세 값이 모두 null이면 그 칸의 행을 삭제한다.</b> 하나라도 차 있으면 만들거나 덮어쓴다.
 * 요청에 없는 칸은 건드리지 않는다.
 *
 * <p>schoolRank는 내신 슬롯에만 보낼 수 있다. 모의고사에 실어 보내면 400이다 —
 * 화면이 칸을 안 그리는 건 안내일 뿐이라 API를 직접 치면 뚫린다.
 */
public record RegularExamSaveRequest(
    @NotNull Long classRoomId,
    @NotNull Short year,
    @NotNull @Valid List<Item> scores
) {
    public record Item(
        @NotNull Long studentId,
        @NotNull RegularExamSlot examSlot,
        @DecimalMin("0") @DecimalMax("100") BigDecimal rawScore,
        @Min(1) @Max(9) Short grade,
        @Min(1) Short schoolRank
    ) {
        /** 셋 다 비었으면 삭제 신호다. */
        public boolean isEmpty() {
            return rawScore == null && grade == null && schoolRank == null;
        }
    }
}
