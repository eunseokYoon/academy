package com.njwenglish.entity.enums;

/**
 * 주차별 테스트 종류. 종류마다 기록하는 값이 다르다.
 *
 * <p>PRACTICE(실전모고)와 RegularExamSlot.MOCK_*(학교 모의고사)는 다른 시험이다.
 * CLINIC(성적 종류)과 clinics 테이블(클리닉 수업 시간 예약)도 다른 것이다.
 */
public enum WeeklyTestType {
    /** 단어 테스트 — 맞힌 개수 + Pass/Fail, 재시험 있음 */
    WORD,
    /** 리뷰 테스트 — Pass/Fail만, 재시험 있음 */
    REVIEW,
    /** 실전 모의고사 — 맞힌 개수만 */
    PRACTICE,
    /** 클리닉 — 내부지문·외부지문 맞힌 개수 */
    CLINIC;

    /** 맞힌 개수와 전체 문항 수를 쓰는 종류다. */
    public boolean usesCount() {
        return this == WORD || this == PRACTICE;
    }

    /** Pass/Fail과 재시험 체크박스가 있는 종류다. */
    public boolean usesResult() {
        return this == WORD || this == REVIEW;
    }

    /** 내부지문·외부지문으로 나뉘는 종류다. */
    public boolean usesSections() {
        return this == CLINIC;
    }
}
