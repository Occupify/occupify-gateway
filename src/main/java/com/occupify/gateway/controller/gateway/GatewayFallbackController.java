package com.occupify.gateway.controller.gateway;

import com.occupify.gateway.controller.AbstractBaseController;
import com.occupify.gateway.dto.base.ErrorResponse;
import com.occupify.gateway.exception.system.SystemErrorCode;
import com.occupify.gateway.filter.CorrelationIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController extends AbstractBaseController {

    private static final String DEFAULT_SERVICE_NAME = "Downstream";

    @RequestMapping(value = { "", "/common" }, method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS })
    public Mono<ResponseEntity<ErrorResponse>> commonFallback(ServerWebExchange exchange) {
        return Mono.just(resolveFallback(exchange, DEFAULT_SERVICE_NAME));
    }

    @RequestMapping(value = "/{service}", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS })
    public Mono<ResponseEntity<ErrorResponse>> serviceFallback(@PathVariable String service,
            ServerWebExchange exchange) {
        String serviceDisplayName = formatServiceName(service);
        return Mono.just(resolveFallback(exchange, serviceDisplayName));
    }

    private ResponseEntity<ErrorResponse> resolveFallback(ServerWebExchange exchange, String serviceName) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(exchange);
        Throwable exception = exchange != null
                ? exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR)
                : null;

        HttpStatus status;
        String errorCode;
        String message;

        if (isTimeout(exception)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            errorCode = SystemErrorCode.GATEWAY_TIMEOUT.getCode();
            message = serviceName + " request timed out. Please try again later.";
            log.warn("[Corr-{}] Fallback triggered: {} request timed out", correlationId, serviceName);
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = SystemErrorCode.SERVICE_UNAVAILABLE.getCode();
            message = serviceName + " is temporarily unavailable. Please try again later.";
            log.warn("[Corr-{}] Fallback triggered: {} is unavailable (reason: {})",
                    correlationId, serviceName, exception != null ? exception.getMessage() : "Circuit breaker open");
        }

        return errorEntity(status, message, errorCode);
    }

    private boolean isTimeout(Throwable exception) {
        if (exception == null) {
            return false;
        }
        if (exception instanceof TimeoutException) {
            return true;
        }
        String exceptionName = exception.getClass().getSimpleName();
        return exceptionName.contains("Timeout") || (exception.getCause() != null && isTimeout(exception.getCause()));
    }

    private String formatServiceName(String rawService) {
        if (rawService == null || rawService.isBlank()) {
            return DEFAULT_SERVICE_NAME;
        }
        return switch (rawService.toLowerCase()) {
            case "core" -> "Core marketplace service";
            case "payment" -> "Payment service";
            case "notification", "noti" -> "Notification service";
            case "administration", "admin" -> "Administration service";
            default -> Character.toUpperCase(rawService.charAt(0)) + rawService.substring(1) + " service";
        };
    }
}
