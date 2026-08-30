package com.accounting.firm.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：生成与解析访问令牌
 * <p>jti（令牌唯一标识）用于登出/改密后的黑名单失效控制</p>
 */
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expireTime;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expire-time:7200000}") long expireTime) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireTime = expireTime;
    }

    /**
     * 生成访问令牌
     *
     * @param userId   用户 ID（写入 claims）
     * @param username 登录账号
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(username)
                .claim("uid", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireTime))
                .signWith(key)
                .compact();
    }

    /**
     * 解析令牌，返回 Claims
     *
     * @throws ExpiredJwtException 令牌已过期
     * @throws RuntimeException    令牌无效
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从令牌中获取登录账号 */
    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /** 从令牌中获取 jti（令牌唯一标识） */
    public String getJtiFromToken(String token) {
        return parseToken(token).getId();
    }

    /** 从令牌中获取过期时间 */
    public Date getExpirationFromToken(String token) {
        return parseToken(token).getExpiration();
    }

    /** 判断令牌是否已过期 */
    public boolean isTokenExpired(String token) {
        try {
            return getExpirationFromToken(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> emptyClaims() {
        return new HashMap<>();
    }
}
