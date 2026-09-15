package com.njwenglish.entity.enums;

/**
 * 정기고사 8슬롯. 연도마다 고정이라 요청에서 목록을 받지 않는다.
 *
 * <p><b>선생님만 본다.</b> 학생·학부모 응답 DTO에 이 값이 들어가면 안 된다.
 *
 * <p><b>등수는 앞의 내신 4개에만 있다.</b> 모의고사에는 받지 않는다(2026-08-24 확정).
 * 판정 정본은 {@link #hasSchoolRank()}이고 DB의 ck_res_rank_slot이 한 번 더 막는다.
 */
public enum RegularExamSlot {
    S1_MIDTERM, S1_FINAL, S2_MIDTERM, S2_FINAL, MOCK_MAR, MOCK_JUN, MOCK_SEP, MOCK_NOV;

    /** 등수를 받는 슬롯인지. 내신만 학교 석차가 나온다. */
    public boolean hasSchoolRank() {
        return this == S1_MIDTERM || this == S1_FINAL || this == S2_MIDTERM || this == S2_FINAL;
    }
}
