package com.njwenglish.entity.enums;

/**
 * 숙제의 종류.
 *
 * <p>GRID는 반 × 수업일 그리드의 열 하나다. 오프라인으로 채점하고, 🔺❌를 받은 학생만
 * 온라인으로 다시 낸다. ONLINE은 처음부터 반 전원이 온라인으로 내는 기존 방식이다.
 */
public enum HomeworkKind {
    GRID,
    ONLINE
}
