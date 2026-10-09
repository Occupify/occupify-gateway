package com.occupify.gateway.security;

import com.occupify.gateway.security.impl.JwtUtilsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    // 64+ bytes secret for HS512
    private static final String TEST_SECRET = "occupify-super-secret-jwt-signing-key-for-unit-testing-must-be-at-least-64-bytes-long!";
    private static final long ACCESS_EXPIRATION_MS = 60000; // 1 minute
    private static final long REFRESH_EXPIRATION_MS = 120000; // 2 minutes

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtilsImpl(TEST_SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        String email = "candidate@occupify.com";
        String userId = UUID.randomUUID().toString();
        String role = "USER";

        String token = jwtUtils.generateAccessToken(email, userId, role);

        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(email, jwtUtils.extractEmail(token));
        assertEquals(userId, jwtUtils.extractUserId(token));
        assertEquals(role, jwtUtils.extractRole(token));
    }

    @Test
    void shouldGenerateAndValidateRefreshToken() {
        String email = "candidate@occupify.com";

        String refreshToken = jwtUtils.generateRefreshToken(email);

        assertNotNull(refreshToken);
        assertTrue(jwtUtils.validateToken(refreshToken));
        assertEquals(email, jwtUtils.extractEmail(refreshToken));
    }

    @Test
    void shouldRejectExpiredToken() throws InterruptedException {
        // Create JwtUtils with 1ms expiration
        JwtUtils expiredJwtUtils = new JwtUtilsImpl(TEST_SECRET, 1, 1);
        String token = expiredJwtUtils.generateAccessToken("test@occupify.com", "123", "USER");

        Thread.sleep(50);

        assertFalse(expiredJwtUtils.validateToken(token));
    }

    @Test
    void shouldRejectTokenWithWrongSignature() {
        String differentSecret = "different-secret-key-that-is-also-at-least-64-bytes-long-for-hs512-testing!!";
        JwtUtils otherJwtUtils = new JwtUtilsImpl(differentSecret, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);

        String token = otherJwtUtils.generateAccessToken("user@occupify.com", "456", "USER");

        assertFalse(jwtUtils.validateToken(token));
    }

    @Test
    void shouldRejectMalformedToken() {
        assertFalse(jwtUtils.validateToken("not-a-valid-jwt-token"));
        assertFalse(jwtUtils.validateToken(""));
        assertFalse(jwtUtils.validateToken(null));
    }

    @Test
    void shouldExtractUserClaimsInSinglePass() {
        String email = "candidate@occupify.com";
        String userId = "user-12345";
        String role = "USER";

        String token = jwtUtils.generateAccessToken(email, userId, role);

        java.util.Optional<UserClaims> claimsOpt = jwtUtils.extractUserClaims(token);
        assertTrue(claimsOpt.isPresent());
        UserClaims claims = claimsOpt.get();
        assertEquals(email, claims.email());
        assertEquals(userId, claims.userId());
        assertEquals(role, claims.role());
    }

    @Test
    void shouldFailFastWhenParametersAreBlank() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.generateAccessToken("", "user-1", "USER"));
        assertThrows(IllegalArgumentException.class,
                () -> jwtUtils.generateAccessToken("email@test.com", "   ", "USER"));
        assertThrows(IllegalArgumentException.class,
                () -> jwtUtils.generateAccessToken("email@test.com", "user-1", null));
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.generateRefreshToken("  "));
    }

    @Test
    void shouldThrowExceptionWhenSecretIsTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtUtilsImpl("too-short-secret", ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS));
    }
}
