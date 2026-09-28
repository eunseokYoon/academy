package com.njwenglish.entity;

import com.njwenglish.entity.enums.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 기기 토큰. 쓰기는 전부 {@code DeviceTokenRepository} 의 네이티브 UPSERT·DELETE 로 한다 —
 * 같은 토큰이 동시에 두 번 들어오면(앱 재시작 직후 등) 찾아서 넣는 방식은 UNIQUE 에 걸려 500 이다.
 * 그래서 이 엔티티에는 생성 메서드가 없다.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;
}
