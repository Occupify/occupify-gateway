package com.occupify.gateway.security;

import io.jsonwebtoken.Claims;

import java.util.Optional;

public interface JwtUtils {

    String CLAIM_USER_ID = "userId";
    String CLAIM_ROLE = "role";
    int HS512_MIN_KEY_BYTES = 64;

    String generateAccessToken(String email, String userId, String role);

    String generateRefreshToken(String email);

    Claims parseClaims(String token);

    Optional<Claims> parseClaimsIfValid(String token);

    Optional<UserClaims> extractUserClaims(String token);

    boolean validateToken(String token);

    String extractEmail(String token);

    String extractUserId(String token);

    String extractRole(String token);

    long getAccessTokenExpiration();

    long getRefreshTokenExpiration();
}
