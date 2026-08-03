package com.njwenglish.repository;

import com.njwenglish.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * 계정을 지울 때 쓴다. revoke는 행을 남겨 두기 때문에
     * refresh_tokens_user_id_fkey에 걸려 users를 지울 수 없다.
     */
    void deleteByUserId(Long userId);
}
