package com.njwenglish.dto.score;

import com.njwenglish.entity.enums.ScoreType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 한 시험의 여러 학생 성적을 한 번에 저장한다. <b>반드시 이 경로를 쓴다.</b>
 * 200명을 한 명씩 폼으로 입력하게 하면 실사용이 불가능하다.
 *
 * <p>시험명·과목·날짜·주차는 상단에서 한 번만 받고, 학생별로는 점수만 받는다.
 * 같은 요청을 두 번 보내도 uq_scores 키로 갱신되어 행이 중복되지 않는다.
 */
public record ScoreBulkCreateRequest(
    @NotNull ScoreType scoreType,
    Long examScheduleId,
    @NotBlank String examName,
    @NotBlank String subject,
    @NotNull LocalDate examDate,
    @NotNull Short year,
    @NotNull Short month,
    @NotNull Short week,
    @NotEmpty @Valid List<Item> scores
) {
    public record Item(
        @NotNull Long studentId,
        BigDecimal rawScore,
        Short gradeLevel,
        String memo
    ) {
    }
}
