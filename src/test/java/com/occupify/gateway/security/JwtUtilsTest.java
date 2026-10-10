package com.occupify.gateway.security;

import com.occupify.gateway.security.impl.JwtUtilsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    // 64+ bytes secret for HS512
    private static final String TEST_SECRET = "occupify-super-secret-jwt-signing-key-for-unit-testing-must-be-at-least-64-bytes-long!";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtilsImpl(TEST_SECRET);
    }

    private String createTestToken(String secret, String email, String userId, String role, long expirationDeltaMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(JwtUtils.CLAIM_USER_ID, userId)
                .claim(JwtUtils.CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationDeltaMs))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    private String createTestToken(String email, String userId, String role) {
        return createTestToken(TEST_SECRET, email, userId, role, 60000);
    }

    @Test
    void shouldValidateTokenAndExtractClaimsFromValidToken() {
        String email = "candidate@occupify.com";
        String userId = UUID.randomUUID().toString();
        String role = "USER";

        String token = createTestToken(email, userId, role);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(email, jwtUtils.extractEmail(token));
        assertEquals(userId, jwtUtils.extractUserId(token));
        assertEquals(role, jwtUtils.extractRole(token));
    }

    @Test
    void shouldExtractUserClaimsInSinglePass() {
        String email = "candidate@occupify.com";
        String userId = "user-12345";
        String role = "USER";

        String token = createTestToken(email, userId, role);

        Optional<UserClaims> claimsOpt = jwtUtils.extractUserClaims(token);
        assertTrue(claimsOpt.isPresent());
        UserClaims claims = claimsOpt.get();
        assertEquals(email, claims.email());
        assertEquals(userId, claims.userId());
        assertEquals(role, claims.role());
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = createTestToken(TEST_SECRET, "test@occupify.com", "123", "USER", -5000);

        assertFalse(jwtUtils.validateToken(token));
        assertTrue(jwtUtils.parseClaimsIfValid(token).isEmpty());
    }

    @Test
    void shouldRejectTokenWithWrongSignature() {
        String differentSecret = "different-secret-key-that-is-also-at-least-64-bytes-long-for-hs512-testing!!";
        String token = createTestToken(differentSecret, "user@occupify.com", "456", "USER", 60000);

        assertFalse(jwtUtils.validateToken(token));
        assertTrue(jwtUtils.parseClaimsIfValid(token).isEmpty());
    }

    @Test
    void shouldRejectMalformedToken() {
        assertFalse(jwtUtils.validateToken("not-a-valid-jwt-token"));
        assertFalse(jwtUtils.validateToken(""));
        assertFalse(jwtUtils.validateToken(null));
        assertTrue(jwtUtils.parseClaimsIfValid("not-a-valid-jwt-token").isEmpty());
        assertTrue(jwtUtils.parseClaimsIfValid("").isEmpty());
        assertTrue(jwtUtils.parseClaimsIfValid(null).isEmpty());
    }

    @Test
    void shouldFailFastWhenTokenIsBlankInParseClaims() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.parseClaims(""));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.parseClaims("   "));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.parseClaims(null));
    }

    @Test
    void shouldThrowExceptionWhenSecretIsTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtilsImpl("too-short-secret"));
    }
}
