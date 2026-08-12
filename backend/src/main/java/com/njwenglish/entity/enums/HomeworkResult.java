package com.njwenglish.entity.enums;

/**
 * 오프라인 채점 결과. null은 "아직 채점 안 함"이고 화면에 회색 "미채점"으로 뜬다.
 *
 * <p>퍼센트는 PARTIAL에만 붙는다. DONE은 100, NOT_DONE은 0이라 숫자가 필요 없다.
 * 결석·책 안 가져옴 같은 사유는 전부 NOT_DONE으로 접는다 — 4번째 상태를 만들지 마라.
 */
public enum HomeworkResult {
    DONE,
    PARTIAL,
    NOT_DONE
}
