package com.njwenglish.service;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.common.security.JwtTokenProvider;
import com.njwenglish.entity.RefreshToken;
import com.njwenglish.entity.User;
import com.njwenglish.entity.enums.UserStatus;
import com.njwenglish.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰의 폐기 여부를 관리한다. 서명·만료는 JwtTokenProvider가 본다.
 *
 * <p>DB에는 원문이 아니라 SHA-256 해시가 들어간다. 조회 키로 써야 해서 BCrypt는 쓸 수 없다.
 * 토큰 자체가 128비트 난수(jti)를 담은 서명 값이라 사전 공격 대상이 아니다.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String issue(User user) {
        String rawToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(
            RefreshToken.issue(user, hash(rawToken), jwtTokenProvider.expiresAt(rawToken)));
        return rawToken;
    }

    /** 만료·폐기·미등록을 구분하지 않는다. 전부 TOKEN_EXPIRED로 내보내고 프론트는 로그인으로 보낸다. */
    @Transactional(readOnly = true)
    public User validate(String rawToken) {
        jwtTokenProvider.parseRefreshToken(rawToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED));

        if (!stored.isUsable(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        // 퇴원 처리는 토큰을 폐기하지만, 그 사이 발급된 것이 남아 있어도 여기서 막힌다
        if (stored.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        return stored.getUser();
    }

    /** 이미 만료·폐기됐거나 없는 토큰으로 호출돼도 조용히 넘어간다. 로그아웃은 항상 성공이다. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
            .ifPresent(token -> token.revoke(OffsetDateTime.now()));
    }

    @Transactional
    public void revokeAllOf(Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
            .forEach(token -> token.revoke(now));
    }

    /**
     * 계정 삭제 직전에 부른다. revokeAllOf는 행을 남겨서
     * refresh_tokens_user_id_fkey에 걸려 users 삭제가 실패한다.
     *
     * <p>한 번이라도 로그인한 계정만 이 행을 갖는다. 그래서 로그인 전 계정을 지울 때는
     * 문제가 드러나지 않는다 — 실제로 쓰던 계정을 지울 때만 터진다.
     */
    @Transactional
    public void deleteAllOf(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
