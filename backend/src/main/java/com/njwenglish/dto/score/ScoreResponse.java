package com.njwenglish.dto.score;

import com.njwenglish.entity.Score;
import com.njwenglish.entity.enums.ScoreType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** T-8 관리용. 수정·삭제에 필요한 scoreId가 들어 있다. */
public record ScoreResponse(
    Long scoreId,
    ScoreType scoreType,
    Long examScheduleId,
    String examName,
    String subject,
    BigDecimal rawScore,
    Short gradeLevel,
    LocalDate examDate,
    Short year,
    Short month,
    Short week,
    String memo
) {
    public static ScoreResponse from(Score score) {
        return new ScoreResponse(
            score.getId(),
            score.getScoreType(),
            score.getExamSchedule() == null ? null : score.getExamSchedule().getId(),
            score.getExamName(),
            score.getSubject(),
            score.getRawScore(),
            score.getGradeLevel(),
            score.getExamDate(),
            score.getYear(),
            score.getMonth(),
            score.getWeek(),
            score.getMemo());
    }
}
