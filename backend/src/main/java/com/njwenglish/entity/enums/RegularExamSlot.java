package com.njwenglish.entity.enums;

/**
 * 정기고사 7슬롯. 연도마다 고정이라 요청에서 목록을 받지 않는다.
 *
 * <p><b>선생님만 본다.</b> 학생·학부모 응답 DTO에 이 값이 들어가면 안 된다.
 */
public enum RegularExamSlot {
    S1_MIDTERM, S1_FINAL, S2_MIDTERM, S2_FINAL, MOCK_MAR, MOCK_JUN, MOCK_SEP
}
