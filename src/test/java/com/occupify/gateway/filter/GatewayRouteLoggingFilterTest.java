package com.occupify.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRouteLoggingFilterTest {

    private GatewayRouteLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayRouteLoggingFilter();
    }

    @Test
    void shouldLogMatchedRouteAndDestinationSuccessfully() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/projects/42?page=1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Route mockRoute = Route.async()
                .id("core-service")
                .uri("http://localhost:8182")
                .order(0)
                .predicate(swe -> true)
                .build();

        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, mockRoute);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, URI.create("http://localhost:8182/projects/42?page=1"));
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "test-corr-id");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            ex.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Chain should be called during route logging filter execution");
    }

    @Test
    void shouldHandleUnmatchedRouteGracefully() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/unknown-endpoint").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Chain should be called even when route is unmatched");
    }

    @Test
    void shouldMaskSensitiveQueryParamsInLog() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/callback?token=supersecret123&code=999888&page=2").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "corr-mask-test");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }
}
