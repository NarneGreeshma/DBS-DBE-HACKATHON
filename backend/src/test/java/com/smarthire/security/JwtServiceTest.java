package com.smarthire.security;

import com.smarthire.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the JWT layer - no Spring context, no database.
 */
class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("smart-hire-test-signing-key-32-bytes!!".getBytes());

    private JwtService service;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setAccessTokenMinutes(30);
        properties.getJwt().setRefreshTokenDays(7);
        service = new JwtService(properties);
        user = new AuthenticatedUser(42L, "someone@smarthire.io", "Someone", "RECRUITER", "hash", true);
    }

    @Test
    @DisplayName("access token round-trips identity and role claims")
    void accessTokenCarriesIdentity() {
        String token = service.createAccessToken(user);

        assertThat(service.isValid(token, JwtService.TYPE_ACCESS)).isTrue();
        assertThat(service.userIdOf(token)).isEqualTo(42L);
        assertThat(service.parse(token).get("email", String.class)).isEqualTo("someone@smarthire.io");
        assertThat(service.parse(token).get(JwtService.CLAIM_ROLES, String.class)).isEqualTo("RECRUITER");
        assertThat(service.parse(token).get(JwtService.CLAIM_TYPE, String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("an access token is never accepted as a refresh token (and vice versa)")
    void tokenTypesAreNotInterchangeable() {
        String access = service.createAccessToken(user);
        String refresh = service.createRefreshToken(user);

        assertThat(service.isValid(access, JwtService.TYPE_REFRESH)).isFalse();
        assertThat(service.isValid(refresh, JwtService.TYPE_ACCESS)).isFalse();
        assertThat(service.isValid(refresh, JwtService.TYPE_REFRESH)).isTrue();
    }

    @Test
    @DisplayName("a token signed with a different key is rejected")
    void tamperedTokenIsRejected() {
        AppProperties other = new AppProperties();
        other.getJwt().setSecret(Base64.getEncoder()
                .encodeToString("a-completely-different-secret-key-16b!".getBytes()));
        String forged = new JwtService(other).createAccessToken(user);

        assertThat(service.isValid(forged, JwtService.TYPE_ACCESS)).isFalse();
        assertThat(service.parse(forged)).isNull();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        String expired = Jwts.builder()
                .subject("42")
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_ACCESS)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256)
                .compact();

        assertThat(service.isValid(expired, JwtService.TYPE_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("a garbage string never blows up the parser")
    void malformedTokenReturnsNull() {
        assertThat(service.parse("not-a-jwt")).isNull();
        assertThat(service.isValid("not-a-jwt", JwtService.TYPE_ACCESS)).isFalse();
        assertThat(service.userIdOf("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("a base64 secret shorter than 32 bytes is hardened with SHA-256")
    void shortSecretIsStillUsable() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("short");
        JwtService hardened = new JwtService(properties);

        String token = hardened.createAccessToken(user);
        assertThat(hardened.isValid(token, JwtService.TYPE_ACCESS)).isTrue();
    }
}
