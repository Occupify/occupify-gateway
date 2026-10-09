package com.occupify.gateway.security;

public record UserClaims(
        String userId,
        String email,
        String role
) {
}
