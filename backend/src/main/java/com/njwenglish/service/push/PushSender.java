package com.njwenglish.service.push;

import java.util.List;
import java.util.Set;

/** 실제 발송. 구현은 FCM 하나와, 키가 없는 로컬·테스트용 no-op 하나다. */
public interface PushSender {

    /**
     * 보내고, <b>지워야 할 토큰</b>(앱을 지운 기기 등)을 돌려준다.
     * 그 밖의 실패는 구현이 로그로 남기고 삼킨다 — 알림은 곁다리다.
     */
    Set<String> send(List<PushMessage> messages);
}
