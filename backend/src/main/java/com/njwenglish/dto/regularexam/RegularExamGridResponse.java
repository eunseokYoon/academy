package com.njwenglish.dto.regularexam;

import com.njwenglish.entity.enums.RegularExamSlot;
import java.math.BigDecimal;
import java.util.List;

/**
 * 정기고사 그리드 한 장. <b>선생님 전용이다.</b>
 *
 * <p>이 DTO를 학생·학부모 응답에 재사용하지 마라. 슬롯 7개는 서버가 고정으로 알기 때문에
 * 열 목록을 내려주지 않는다 — 프론트가 enum 순서대로 그린다.
 */
public record RegularExamGridResponse(
    Long classRoomId,
    short year,
    List<StudentRow> students,
    List<ScoreItem> scores
) {
    /** enrolled=false는 지금은 재원생이 아니지만 그 해 점수가 남아 있는 학생이다. */
    public record StudentRow(Long studentId, String name, boolean enrolled) {
    }

    public record ScoreItem(Long studentId, RegularExamSlot examSlot, BigDecimal rawScore) {
    }
}
