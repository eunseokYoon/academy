package com.njwenglish.dto.score;

import com.njwenglish.entity.enums.ScoreType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * <b>WORD는 100점 만점 환산값만 저장한다.</b> 25문항 중 20개면 80.00이다.
 * 환산은 T-8 입력 화면에서 하고 원점수를 그대로 보내지 마라 —
 * 문항 수가 주마다 달라져서 P-4 주차별 그래프의 세로축이 무너진다.
 *
 * <p>year·month·week는 세 종류 모두 필수다. 화면에서 고른 주차를 그대로 싣는다.
 * 서버가 examDate에서 계산하지 않는다.
 *
 * <p>과목은 자유 문자열이다. 성적 관리 범위(영어만인지 전 과목인지)가 미확정이라
 * 코드에서 "영어"로 고정하지 마라.
 */
public record ScoreCreateRequest(
    @NotNull ScoreType scoreType,
    Long examScheduleId,
    @NotBlank String examName,
    @NotBlank String subject,
    BigDecimal rawScore,
    Short gradeLevel,
    @NotNull LocalDate examDate,
    @NotNull Short year,
    @NotNull Short month,
    @NotNull Short week,
    String memo
) {
}
