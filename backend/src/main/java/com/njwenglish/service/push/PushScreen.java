package com.njwenglish.service.push;

/**
 * 딥링크 목적지. payload 의 {@code screen} 값이고 앱의 라우팅과 짝이다 — 이름을 바꾸면
 * 이미 깔린 앱이 알림을 눌러도 홈에 떨어진다. 추가만 해라.
 */
public enum PushScreen {
    NOTICE("notice"),
    HOMEWORK("homework"),
    SCORES("scores"),
    QNA("qna"),
    ATTENDANCE("attendance"),
    SCHEDULE("schedule"),
    LESSON("lesson"),
    REPORT("report");

    private final String value;

    PushScreen(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
