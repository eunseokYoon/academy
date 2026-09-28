package com.njwenglish.service.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM HTTP v1. iOS 는 Firebase 가 APNs 로 넘긴다(APNs 키를 Firebase 콘솔에 올려 둬야 한다).
 *
 * <p>로그에 토큰·문구를 남기지 않는다 — 문구에 자녀 이름이 들어 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    /** sendEach 한 번의 상한. */
    private static final int BATCH = 500;

    private final FirebaseMessaging messaging;

    @Override
    public Set<String> send(List<PushMessage> messages) {
        Set<String> stale = new HashSet<>();
        for (int from = 0; from < messages.size(); from += BATCH) {
            List<PushMessage> chunk = messages.subList(from,
                Math.min(from + BATCH, messages.size()));
            try {
                BatchResponse response = messaging.sendEach(
                    chunk.stream().map(FcmPushSender::toMessage).toList());
                collectStale(chunk, response.getResponses(), stale);
            } catch (FirebaseMessagingException e) {
                // 묶음 전체 실패(인증·네트워크). 토큰 탓이 아니니 지우지 않는다
                log.warn("FCM 발송 실패 {}건 code={}", chunk.size(), e.getMessagingErrorCode(), e);
            }
        }
        return stale;
    }

    /**
     * 앱을 지웠거나 토큰이 망가진 기기. 지우지 않으면 매번 발송을 시도한다.
     * 그 밖의 실패(일시 오류 등)는 토큰을 남긴다.
     */
    static void collectStale(List<PushMessage> chunk, List<SendResponse> responses,
                             Set<String> stale) {
        int failed = 0;
        for (int i = 0; i < responses.size(); i++) {
            SendResponse each = responses.get(i);
            if (each.isSuccessful()) {
                continue;
            }
            failed++;
            MessagingErrorCode code = each.getException() == null
                ? null : each.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT) {
                stale.add(chunk.get(i).token());
            }
        }
        if (failed > 0) {
            log.info("FCM 개별 실패 {}/{}건, 정리 대상 {}건", failed, responses.size(), stale.size());
        }
    }

    /**
     * {@code setToken} 은 9.11 에서 {@code setFid}(설치 ID) 쪽으로 가며 deprecated 표시가 붙었다.
     * 앱의 firebase_messaging {@code getToken()} 이 주는 값은 등록 토큰이라 여기가 맞다.
     * 앱이 설치 ID 로 옮겨 가면 그때 같이 바꾼다.
     */
    @SuppressWarnings("deprecation")
    private static Message toMessage(PushMessage message) {
        return Message.builder()
            .setToken(message.token())
            .setNotification(Notification.builder().setTitle(message.title()).build())
            .putAllData(message.data())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build())
            .setApnsConfig(ApnsConfig.builder()
                .setAps(Aps.builder().setSound("default").build())
                .build())
            .build();
    }
}
