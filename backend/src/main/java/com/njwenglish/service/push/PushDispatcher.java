package com.njwenglish.service.push;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <b>커밋이 끝난 뒤에</b> 보낸다. 커밋 전에 보내면 롤백돼도 알림은 이미 갔다.
 *
 * <p><b>발송 실패가 본 작업을 되돌리지 않는다</b> — 여기서 나는 예외는 전부 로그로 끝난다.
 * 온라인 테스트의 성적 자동 반영과 같은 원칙이다: 제출은 성공해야 하고 알림은 곁다리다.
 *
 * <p>조용한 시간·보류 큐·스케줄러는 없다(밤에도 보낸다, 확정). 언제나 커밋 직후 즉시다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    private final PushPlanner planner;
    private final PushSender sender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PushEvent event) {
        try {
            List<PushMessage> messages = planner.plan(event);
            if (messages.isEmpty()) {
                return;
            }
            Set<String> stale = sender.send(messages);
            planner.purge(stale);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패 topic={} students={}", event.topic(),
                event.studentIds().size(), e);
        }
    }
}
