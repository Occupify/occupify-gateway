package com.occupify.gateway.exception.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {

    AUTH_000("AUTH_000", "Invalid email format", HttpStatus.BAD_REQUEST),
    AUTH_001("AUTH_001", "Email already exists in the system", HttpStatus.CONFLICT),
    AUTH_002("AUTH_002", "Password must be at least 8 characters long", HttpStatus.BAD_REQUEST),
    AUTH_003("AUTH_003", "Invalid email or password", HttpStatus.UNAUTHORIZED),
    AUTH_004("AUTH_004", "Refresh token is required", HttpStatus.BAD_REQUEST),
    AUTH_005("AUTH_005", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    AUTH_006("AUTH_006", "Refresh token has expired or been revoked", HttpStatus.UNAUTHORIZED),
    AUTH_007("AUTH_007", "Please wait before requesting a new OTP", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_008("AUTH_008", "OTP has expired or does not exist", HttpStatus.BAD_REQUEST),
    AUTH_009("AUTH_009", "Invalid OTP code", HttpStatus.BAD_REQUEST),
    AUTH_010("AUTH_010", "Maximum OTP verification attempts exceeded", HttpStatus.TOO_MANY_REQUESTS),
    AUTH_011("AUTH_011", "Failed to dispatch notification email", HttpStatus.INTERNAL_SERVER_ERROR),
    AUTH_012("AUTH_012", "User not found", HttpStatus.NOT_FOUND),
    AUTH_013("AUTH_013", "Current password does not match", HttpStatus.BAD_REQUEST),
    AUTH_014("AUTH_014", "New password cannot be identical to current password", HttpStatus.BAD_REQUEST),
    AUTH_015("AUTH_015", "Account is already activated", HttpStatus.BAD_REQUEST),
    AUTH_016("AUTH_016", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
    AUTH_017("AUTH_017", "Confirm password is different", HttpStatus.BAD_REQUEST),
    USER_001("USER_001", "User account not found", HttpStatus.NOT_FOUND),
    USER_007("USER_007", "User account is inactive or locked", HttpStatus.FORBIDDEN),
    USER_008("USER_008", "User account has been banned", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    public String getDefaultMessage() {
        return message;
    }
}
