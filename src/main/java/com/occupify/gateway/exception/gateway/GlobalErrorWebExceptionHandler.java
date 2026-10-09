package com.occupify.gateway.exception.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.occupify.gateway.dto.base.ErrorResponse;
import com.occupify.gateway.exception.BaseException;
import com.occupify.gateway.filter.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@Order(GlobalErrorWebExceptionHandler.ORDER)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    // Higher precedence than default Spring WebFlux ErrorWebExceptionHandler (ORDER = -1)
    public static final int ORDER = -2;

    public static final String UNKNOWN_CORRELATION_ID = CorrelationIdFilter.UNKNOWN_CORRELATION_ID;
    private static final String DEFAULT_ERROR_PHRASE = "Gateway Error";
    private static final String DEFAULT_DOWNSTREAM_OFFLINE_MSG = "Downstream microservice is unreachable or offline";
    private static final String DEFAULT_CIRCUIT_OPEN_MSG = "Downstream service circuit breaker is open";
    private static final String DEFAULT_TIMEOUT_MSG = "Downstream service request timed out";
    private static final String DEFAULT_UNEXPECTED_ERROR_MSG = "Unexpected gateway error";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ErrorDetails details = resolveErrorDetails(ex);
        logError(exchange, details, ex);

        return writeError(exchange, details.status(), details.message(), details.errorCode());
    }

    public Mono<Void> writeError(ServerWebExchange exchange, HttpStatusCode status, String message) {
        return writeError(exchange, status, message, null);
    }

    public Mono<Void> writeError(ServerWebExchange exchange, HttpStatusCode status, String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String correlationId = resolveCorrelationId(exchange);
        String reasonPhrase = (status instanceof HttpStatus hs) ? hs.getReasonPhrase() : DEFAULT_ERROR_PHRASE;
        String resolvedMessage = message != null ? message : reasonPhrase;

        ErrorResponse errorResponse = new ErrorResponse(
                status.value(),
                resolvedMessage,
                errorCode
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("[Corr-{}] Failed to serialize ErrorResponse to JSON: {}", correlationId, e.getMessage(), e);
            bytes = buildFallbackJson(errorResponse);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    public String resolveCorrelationId(ServerWebExchange exchange) {
        return CorrelationIdFilter.resolveCorrelationId(exchange);
    }

    private ErrorDetails resolveErrorDetails(Throwable ex) {
        if (ex instanceof BaseException be) {
            return new ErrorDetails(be.getStatus(), be.getMessage(), be.getCode());
        }
        if (ex instanceof ResponseStatusException rse) {
            String reason = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            return new ErrorDetails(rse.getStatusCode(), reason, null);
        }
        if (ex instanceof CallNotPermittedException) {
            return new ErrorDetails(HttpStatus.SERVICE_UNAVAILABLE, DEFAULT_CIRCUIT_OPEN_MSG, "SYS_008");
        }
        if (isTimeoutException(ex)) {
            return new ErrorDetails(HttpStatus.GATEWAY_TIMEOUT, DEFAULT_TIMEOUT_MSG, "SYS_009");
        }
        if (ex instanceof ConnectException) {
            return new ErrorDetails(HttpStatus.SERVICE_UNAVAILABLE, DEFAULT_DOWNSTREAM_OFFLINE_MSG, "SYS_008");
        }
        return new ErrorDetails(HttpStatus.INTERNAL_SERVER_ERROR, DEFAULT_UNEXPECTED_ERROR_MSG, null);
    }

    private boolean isTimeoutException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        if (ex instanceof TimeoutException) {
            return true;
        }
        String name = ex.getClass().getSimpleName();
        return name.contains("Timeout") || (ex.getCause() != null && isTimeoutException(ex.getCause()));
    }

    private void logError(ServerWebExchange exchange, ErrorDetails details, Throwable ex) {
        String path = exchange.getRequest().getURI().getPath();
        String correlationId = resolveCorrelationId(exchange);

        if (details.status().is5xxServerError()) {
            log.error("[Corr-{}] Server error handling request for [{}]: Status {} - {}",
                    correlationId, path, details.status().value(), details.message(), ex);
        } else {
            log.warn("[Corr-{}] Client error handling request for [{}]: Status {} - {}",
                    correlationId, path, details.status().value(), details.message());
        }
    }

    private byte[] buildFallbackJson(ErrorResponse response) {
        String json = "{\"statusCode\":" + response.statusCode() + ","
                + "\"message\":\"" + escapeJson(response.message()) + "\""
                + (response.errorCode() != null ? ",\"errorCode\":\"" + escapeJson(response.errorCode()) + "\"" : "")
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private record ErrorDetails(HttpStatusCode status, String message, String errorCode) {
    }
}
