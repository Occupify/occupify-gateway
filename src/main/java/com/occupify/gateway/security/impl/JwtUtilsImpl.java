package com.occupify.gateway.security.impl;

import com.occupify.gateway.security.JwtUtils;
import com.occupify.gateway.security.UserClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtilsImpl implements JwtUtils {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtilsImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration:3600000}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < HS512_MIN_KEY_BYTES) {
            throw new IllegalArgumentException("JWT secret key must be at least " + HS512_MIN_KEY_BYTES + " bytes (512 bits) long for HS512.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    public String generateAccessToken(String email, String userId, String role) {
        validateNonBlank(email, "Subject email");
        validateNonBlank(userId, "User ID");
        validateNonBlank(role, "User role");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(String email) {
        validateNonBlank(email, "Subject email");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public Claims parseClaims(String token) {
        validateNonBlank(token, "JWT token");
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public Optional<Claims> parseClaimsIfValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseClaims(token));
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Malformed or invalid JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<UserClaims> extractUserClaims(String token) {
        return parseClaimsIfValid(token).map(claims -> new UserClaims(
                claims.get(CLAIM_USER_ID, String.class),
                claims.getSubject(),
                claims.get(CLAIM_ROLE, String.class)
        ));
    }

    @Override
    public boolean validateToken(String token) {
        return parseClaimsIfValid(token).isPresent();
    }

    @Override
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    @Override
    public String extractUserId(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, String.class);
    }

    @Override
    public String extractRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    @Override
    public long getAccessTokenExpiration() {
        return accessTokenExpirationMs;
    }

    @Override
    public long getRefreshTokenExpiration() {
        return refreshTokenExpirationMs;
    }

    private void validateNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank.");
        }
    }
}
