package com.njwenglish.service.push;

import com.njwenglish.repository.DeviceTokenRepository;
import com.njwenglish.repository.PushDailySendRepository;
import com.njwenglish.repository.PushRecipientRepository.RecipientRow;
import com.njwenglish.repository.PushRecipientRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 → 기기별 메시지. DB 일은 전부 여기서 하고 발송(HTTP)은 트랜잭션 밖에서 한다.
 *
 * <p><b>REQUIRES_NEW 를 빼지 마라.</b> 커밋 후 리스너 안에서 REQUIRED 로 부르면 이미 끝난
 * 원래 트랜잭션에 합류해, 하루 1건 표식(INSERT)이 커밋되지 않고 조용히 사라진다.
 */
@Component
@RequiredArgsConstructor
public class PushPlanner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PushRecipientRepository recipientRepository;
    private final DeviceTokenRepository tokenRepository;
    private final PushDailySendRepository dailySendRepository;

    /**
     * 수신자는 계정 단위로 한 번이다. 한 학부모에게 자녀 둘이 같은 반이면 반 공지가 두 번
     * 갈 수 있는데, 먼저 나온 자녀 하나로 한 번만 보낸다(이름순이라 매번 같은 자녀다).
     *
     * <p>조용히 건너뛰는 경우는 전부 정상이다 — 미가입 학생(user 없음), 학부모 없음,
     * 토큰 없음(앱을 안 깔았다), 알림 끔, 그날 이미 받음.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<PushMessage> plan(PushEvent event) {
        if (event.isEmpty()) {
            return List.of();
        }
        Map<Long, Draft> drafts = draftsOf(event,
            recipientRepository.findRecipients(event.studentIds()));
        if (drafts.isEmpty()) {
            return List.of();
        }

        Map<Long, List<String>> tokensByUser = tokenRepository
            .findDeliverable(drafts.keySet()).stream()
            .collect(Collectors.groupingBy(DeviceTokenRepository.TokenRow::getUserId,
                LinkedHashMap::new,
                Collectors.mapping(DeviceTokenRepository.TokenRow::getToken,
                    Collectors.toList())));

        String dailyKind = event.topic().dailyKind();
        LocalDate today = LocalDate.now(KST);
        List<PushMessage> messages = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : tokensByUser.entrySet()) {
            // 토큰이 있는 사람만 표식을 남긴다. 앱이 없는 사람 몫까지 쌓을 이유가 없다
            if (dailyKind != null
                && dailySendRepository.claim(entry.getKey(), dailyKind, today) == 0) {
                continue;
            }
            Draft draft = drafts.get(entry.getKey());
            for (String token : entry.getValue()) {
                messages.add(new PushMessage(token, draft.title(), draft.data()));
            }
        }
        return messages;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            tokenRepository.deleteByTokens(tokens);
        }
    }

    static Map<Long, Draft> draftsOf(PushEvent event, List<RecipientRow> rows) {
        PushTopic topic = event.topic();
        Map<Long, Draft> drafts = new LinkedHashMap<>();
        for (RecipientRow row : rows) {
            if (event.toStudent() && topic.studentScreen() != null
                && row.getStudentUserId() != null) {
                drafts.putIfAbsent(row.getStudentUserId(), new Draft(
                    topic.studentTitle(event.label()),
                    data(topic.studentScreen(), event.targetId(), null)));
            }
            if (event.toParent() && topic.parentScreen() != null
                && row.getParentUserId() != null) {
                drafts.putIfAbsent(row.getParentUserId(), new Draft(
                    topic.parentTitle(row.getStudentName(), event.label()),
                    data(topic.parentScreen(), event.targetId(), row.getStudentId())));
            }
        }
        return drafts;
    }

    /** 학부모 알림에는 studentId 가 붙는다 — 앱이 그 자녀를 선택한 채로 화면을 연다. */
    private static Map<String, String> data(PushScreen screen, Long targetId, Long studentId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("screen", screen.value());
        if (targetId != null) {
            data.put("id", targetId.toString());
        }
        if (studentId != null) {
            data.put("studentId", studentId.toString());
        }
        return Map.copyOf(data);
    }

    record Draft(String title, Map<String, String> data) {
    }
}
