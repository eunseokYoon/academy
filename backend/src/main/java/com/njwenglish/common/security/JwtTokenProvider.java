package com.njwenglish.common.security;

import com.njwenglish.common.error.BusinessException;
import com.njwenglish.common.error.ErrorCode;
import com.njwenglish.entity.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰은 메모리에만 두고 리프레시 토큰은 HttpOnly 쿠키로 나간다.
 * 리프레시 토큰의 폐기 여부는 refresh_tokens 테이블이 판단한다(TokenService).
 * 여기서는 서명과 만료만 본다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
        @Value("${app.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(Long userId, UserRole role, boolean mustChangePassword) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_ROLE, role.name())
            .claim(CLAIM_MUST_CHANGE_PASSWORD, mustChangePassword)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessTokenValiditySeconds * 1000))
            .signWith(key)
            .compact();
    }

    public AuthUser parseAccessToken(String token) {
        Claims claims = parse(token);
        return new AuthUser(
            Long.valueOf(claims.getSubject()),
            UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
            Boolean.TRUE.equals(claims.get(CLAIM_MUST_CHANGE_PASSWORD, Boolean.class)));
    }

    /** 같은 사용자라도 매번 다른 값이어야 한다. 발급 이력을 행 단위로 폐기하기 때문이다. */
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshTokenValiditySeconds * 1000))
            .signWith(key)
            .compact();
    }

    public Long parseRefreshToken(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    /** refresh_tokens.expires_at에 그대로 넣는 값. 만료 시각의 출처를 한 곳으로 둔다. */
    public OffsetDateTime expiresAt(String token) {
        return parse(token).getExpiration().toInstant()
            .atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }
}
