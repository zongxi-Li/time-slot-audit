/**
 * 文件职责：负责 JWT 的签发和验证解析。
 * 接口：被 AuthService 和认证过滤器调用。
 * 方法：issue(用户) 生成登录令牌；parse(token) 校验令牌并还原认证用户。
*/

package com.timeslot.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${timeslot.jwt.secret}") String secret,
                      @Value("${timeslot.jwt.expiration-seconds}") long expirationSeconds) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "缺少 JWT 密钥：请通过环境变量 JWT_SECRET 显式提供，真实环境禁止使用默认密钥");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥强度不足：JWT_SECRET 至少需要 32 个字符");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.userId())
                .claim("username", user.getUsername())
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Number userId = claims.get("userId", Number.class);
        return new AuthenticatedUser(userId.longValue(), claims.get("username", String.class), claims.get("role", String.class));
    }
}
