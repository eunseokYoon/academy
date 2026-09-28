package com.njwenglish.service.push;

import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** 서비스 계정 키가 없을 때(로컬·테스트). 보내지 않고 건수만 남긴다. */
@Slf4j
public class NoopPushSender implements PushSender {

    @Override
    public Set<String> send(List<PushMessage> messages) {
        log.debug("푸시 비활성 — {}건 건너뜀", messages.size());
        return Set.of();
    }
}
