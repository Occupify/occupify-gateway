package com.occupify.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements WebFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    public static final String UNKNOWN_CORRELATION_ID = "unknown";

    public static String resolveCorrelationId(ServerWebExchange exchange) {
        if (exchange == null) {
            return UNKNOWN_CORRELATION_ID;
        }
        String correlationId = exchange.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        if (exchange.getRequest() != null && exchange.getRequest().getHeaders() != null) {
            String headerId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
            if (headerId != null && !headerId.isBlank()) {
                return headerId;
            }
        }
        return UNKNOWN_CORRELATION_ID;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Store in exchange attributes for downstream filters
        exchange.getAttributes().put(CORRELATION_ID_ATTRIBUTE, correlationId);

        // Mutate request to ensure downstream services receive the header
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Mutate response to return the correlation ID to the client
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
