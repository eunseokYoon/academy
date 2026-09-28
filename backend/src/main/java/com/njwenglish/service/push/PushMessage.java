package com.njwenglish.service.push;

import java.util.Map;

/** 기기 하나로 가는 메시지. data 에는 딥링크(screen·id·studentId)만 있다 — 내용을 넣지 마라. */
public record PushMessage(String token, String title, Map<String, String> data) {
}
