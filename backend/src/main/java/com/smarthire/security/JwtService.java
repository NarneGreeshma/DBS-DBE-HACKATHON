package com.smarthire.security;

import com.smarthire.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates HS256 JSON Web Tokens.
 * Access tokens are short-lived and carry identity claims; refresh tokens are long-lived
 * and carry a rotating {@code jti} so they can be revoked by re-issue.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getJwt().getSecret());
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("smarthire.jwt.secret must be configured");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            try {
                bytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to derive JWT signing key", ex);
            }
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.id()))
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim(CLAIM_ROLES, user.role())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwt().getAccessTokenMinutes() * 60)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwt().getRefreshTokenDays() * 86400)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** @return the raw claims, or {@code null} when the token is invalid/expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isValid(String token, String expectedType) {
        Claims claims = parse(token);
        return claims != null
                && expectedType.equals(claims.get(CLAIM_TYPE, String.class))
                && claims.getExpiration() != null
                && claims.getExpiration().after(new Date());
    }

    public Long userIdOf(String token) {
        Claims claims = parse(token);
        if (claims == null || claims.getSubject() == null) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.getJwt().getAccessTokenMinutes() * 60;
    }

    public Map<String, Object> describe(long userId, String role) {
        return Map.of("sub", userId, "roles", role, "typ", TYPE_ACCESS);
    }
}
