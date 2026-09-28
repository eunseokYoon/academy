package com.njwenglish.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.njwenglish.service.push.FcmPushSender;
import com.njwenglish.service.push.NoopPushSender;
import com.njwenglish.service.push.PushSender;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FCM 서비스 계정 키는 <b>파일 경로를 환경변수로</b> 받는다. 키를 저장소나 이미지에 넣지 마라.
 *
 * <p>비어 있으면 발송을 끄고 부팅한다(2026-09-27 결정) — 로컬·테스트가 Firebase 없이 돈다.
 * 운영에서 빠지면 알림만 안 가고 나머지는 멀쩡하므로, 부팅 로그의 경고를 확인해라.
 * 경로를 줬는데 읽지 못하면 부팅을 멈춘다 — 그건 설정 실수다.
 */
@Slf4j
@Configuration
public class PushConfig {

    @Bean
    public PushSender pushSender(@Value("${app.push.firebase-credentials:}") String path) {
        if (path == null || path.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS 가 비어 있어 푸시 알림을 보내지 않습니다");
            return new NoopPushSender();
        }
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(in))
                .build();
            FirebaseApp app = FirebaseApp.initializeApp(options, "academy-push");
            return new FcmPushSender(FirebaseMessaging.getInstance(app));
        } catch (IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 키를 읽을 수 없습니다: " + path, e);
        }
    }
}
