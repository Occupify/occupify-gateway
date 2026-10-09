package com.occupify.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/projects").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            downstreamHeader.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        String correlationIdAttr = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        assertNotNull(correlationIdAttr, "Correlation ID attribute should be set");
        assertFalse(correlationIdAttr.isBlank(), "Correlation ID should not be blank");
        assertEquals(correlationIdAttr, downstreamHeader.get(), "Downstream request should receive correlation ID header");
        assertEquals(correlationIdAttr, exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER), "Response should include correlation ID header");
    }

    @Test
    void shouldPreserveExistingCorrelationId() {
        String existingId = "client-correlation-12345";
        MockServerHttpRequest request = MockServerHttpRequest.get("/projects")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<String> downstreamHeader = new AtomicReference<>();
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            downstreamHeader.set(ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals(existingId, exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE));
        assertEquals(existingId, downstreamHeader.get());
        assertEquals(existingId, exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void shouldResolveCorrelationIdFromAttributeOrHeader() {
        // Null exchange
        assertEquals(CorrelationIdFilter.UNKNOWN_CORRELATION_ID, CorrelationIdFilter.resolveCorrelationId(null));

        // Exchange with attribute
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/").build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        ex1.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "attr-corr-id");
        assertEquals("attr-corr-id", CorrelationIdFilter.resolveCorrelationId(ex1));

        // Exchange with header only
        MockServerHttpRequest req2 = MockServerHttpRequest.get("/")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "header-corr-id")
                .build();
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
        assertEquals("header-corr-id", CorrelationIdFilter.resolveCorrelationId(ex2));

        // Exchange with neither
        MockServerHttpRequest req3 = MockServerHttpRequest.get("/").build();
        MockServerWebExchange ex3 = MockServerWebExchange.from(req3);
        assertEquals(CorrelationIdFilter.UNKNOWN_CORRELATION_ID, CorrelationIdFilter.resolveCorrelationId(ex3));
    }
}
