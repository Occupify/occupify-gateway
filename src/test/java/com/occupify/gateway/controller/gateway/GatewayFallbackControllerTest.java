package com.occupify.gateway.controller.gateway;

import com.occupify.gateway.dto.base.ErrorResponse;
import com.occupify.gateway.exception.system.SystemErrorCode;
import com.occupify.gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class GatewayFallbackControllerTest {

    private GatewayFallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayFallbackController();
    }

    @Test
    void shouldReturnServiceUnavailableWhenCircuitBreakerTripsForCore() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/core").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "corr-test-123");

        ResponseEntity<ErrorResponse> response = controller.serviceFallback("core", exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().statusCode());
        assertEquals(SystemErrorCode.SERVICE_UNAVAILABLE.getCode(), response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("Core marketplace service is temporarily unavailable"));
    }

    @Test
    void shouldReturnGatewayTimeoutWhenTimeoutExceptionOccurs() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/payment").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                new TimeoutException("Timeout connecting to payment"));

        ResponseEntity<ErrorResponse> response = controller.serviceFallback("payment", exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(504, response.getBody().statusCode());
        assertEquals(SystemErrorCode.GATEWAY_TIMEOUT.getCode(), response.getBody().errorCode());
        assertTrue(response.getBody().message().contains("Payment service request timed out"));
    }

    @Test
    void shouldFormatNotificationAndAdministrationServiceNames() {
        MockServerHttpRequest notiRequest = MockServerHttpRequest.get("/fallback/notification").build();
        MockServerWebExchange notiExchange = MockServerWebExchange.from(notiRequest);
        ResponseEntity<ErrorResponse> notiResponse = controller.serviceFallback("notification", notiExchange).block();
        assertNotNull(notiResponse);
        assertTrue(notiResponse.getBody().message().contains("Notification service is temporarily unavailable"));

        MockServerHttpRequest adminRequest = MockServerHttpRequest.get("/fallback/admin").build();
        MockServerWebExchange adminExchange = MockServerWebExchange.from(adminRequest);
        ResponseEntity<ErrorResponse> adminResponse = controller.serviceFallback("admin", adminExchange).block();
        assertNotNull(adminResponse);
        assertTrue(adminResponse.getBody().message().contains("Administration service is temporarily unavailable"));
    }

    @Test
    void shouldHandleCommonFallbackEndpoint() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/common").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseEntity<ErrorResponse> response = controller.commonFallback(exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("Downstream is temporarily unavailable"));
    }

    @Test
    void shouldDetectNestedTimeoutException() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/custom").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        RuntimeException nestedEx = new RuntimeException("Wrapped exception", new TimeoutException("Cause"));
        exchange.getAttributes().put(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, nestedEx);

        ResponseEntity<ErrorResponse> response = controller.serviceFallback("custom", exchange).block();

        assertNotNull(response);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals(SystemErrorCode.GATEWAY_TIMEOUT.getCode(), response.getBody().errorCode());
    }
}
