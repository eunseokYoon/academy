package com.njwenglish.repository;

import com.njwenglish.entity.DeviceToken;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    /**
     * 등록. <b>같은 토큰이 이미 있으면 user_id 를 지금 사용자로 옮긴다.</b>
     * 한 기기를 다른 계정으로 로그인한 것이다 — 옮기지 않으면 앞 사람의 알림이 계속 온다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO device_tokens (user_id, token, platform, created_at, last_seen_at)
        VALUES (:userId, :token, :platform, now(), now())
        ON CONFLICT (token) DO UPDATE
        SET user_id = EXCLUDED.user_id,
            platform = EXCLUDED.platform,
            last_seen_at = now()
        """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("token") String token,
                @Param("platform") String platform);

    /** 로그아웃. 남의 토큰은 지우지 않는다 — 내 행일 때만 지운다. */
    @Modifying
    @Query(value = "DELETE FROM device_tokens WHERE token = :token AND user_id = :userId",
        nativeQuery = true)
    int deleteMine(@Param("userId") Long userId, @Param("token") String token);

    /** FCM 이 UNREGISTERED·INVALID_ARGUMENT 로 거절한 토큰. 앱을 지운 기기다. */
    @Modifying
    @Query(value = "DELETE FROM device_tokens WHERE token IN (:tokens)", nativeQuery = true)
    int deleteByTokens(@Param("tokens") Collection<String> tokens);

    /**
     * 실제로 보낼 수 있는 토큰. 알림을 껐거나 비활성(퇴원) 계정이면 빠진다.
     * <b>선생님 행은 등록에서 이미 막지만 여기서도 거른다</b> — 선생님은 알림을 받지 않는다.
     */
    @Query("""
        SELECT d.user.id AS userId, d.token AS token
        FROM DeviceToken d
        WHERE d.user.id IN :userIds
          AND d.user.pushEnabled = true
          AND d.user.status = 'ACTIVE'
          AND d.user.role IN ('STUDENT', 'PARENT')
        """)
    List<TokenRow> findDeliverable(@Param("userIds") Collection<Long> userIds);

    interface TokenRow {
        Long getUserId();

        String getToken();
    }
}
