package com.occupify.gateway.exception.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SystemErrorCode {

    INTERNAL_ERROR("SYS_001", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("SYS_002", "Validation failed", HttpStatus.BAD_REQUEST),
    DATABASE_ERROR("SYS_003", "Database error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    RESOURCE_NOT_FOUND("SYS_004", "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("SYS_005", "HTTP method not supported", HttpStatus.METHOD_NOT_ALLOWED),
    UNAUTHORIZED("SYS_006", "Unauthorized access", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("SYS_007", "Access denied", HttpStatus.FORBIDDEN),
    SERVICE_UNAVAILABLE("SYS_008", "Downstream service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_TIMEOUT("SYS_009", "Downstream service request timed out", HttpStatus.GATEWAY_TIMEOUT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
