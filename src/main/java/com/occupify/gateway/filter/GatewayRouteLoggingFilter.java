package com.occupify.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;

@Slf4j
@Component
public class GatewayRouteLoggingFilter implements GlobalFilter, Ordered {

    public static final String DEFAULT_CORRELATION_ID = "N/A";
    public static final String UNKNOWN_CLIENT_IP = "unknown";
    public static final String UNMATCHED_ROUTE = "unmatched-route";
    public static final String UNRESOLVED_DESTINATION = "unresolved";
    public static final String UNKNOWN_STATUS = "UNKNOWN";
    public static final String QUERY_SEPARATOR = "?";

    private static final java.util.regex.Pattern SENSITIVE_QUERY_PATTERN =
            java.util.regex.Pattern.compile("(?i)(token|password|secret|code|otp|access_token|refresh_token|api_key|key)=[^&]*");

    private static final String LOG_ROUTING_REQUEST =
            "[Corr-{}] Routing request: {} {} -> Route [{}] Target [{}] | Client IP: {}";
    private static final String LOG_COMPLETED_REQUEST =
            "[Corr-{}] Completed request: {} {} -> Route [{}] with status [{}] (elapsed: {}ms)";

    public record RouteLogContext(
            String correlationId,
            String method,
            String fullPath,
            String routeId,
            long startTime
    ) {
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String fullPath = buildFullPath(request);
        String correlationId = resolveCorrelationId(exchange);
        String clientIp = extractClientIp(request);

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        URI targetUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String routeId = (route != null) ? route.getId() : UNMATCHED_ROUTE;
        String destination = resolveDestination(route, targetUri);

        log.info(LOG_ROUTING_REQUEST, correlationId, method, fullPath, routeId, destination, clientIp);

        RouteLogContext context = new RouteLogContext(correlationId, method, fullPath, routeId, startTime);
        return chain.filter(exchange).doFinally(signalType ->
                logCompletedRequest(exchange, context)
        );
    }

    private String buildFullPath(ServerHttpRequest request) {
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        if (query != null && !query.isBlank()) {
            return path + QUERY_SEPARATOR + maskSensitiveQueryParams(query);
        }
        return path;
    }

    private String maskSensitiveQueryParams(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        return SENSITIVE_QUERY_PATTERN.matcher(query).replaceAll("$1=***");
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String correlationId = CorrelationIdFilter.resolveCorrelationId(exchange);
        return CorrelationIdFilter.UNKNOWN_CORRELATION_ID.equals(correlationId) ? DEFAULT_CORRELATION_ID : correlationId;
    }

    private String extractClientIp(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return (remoteAddress != null && remoteAddress.getAddress() != null)
                ? remoteAddress.getAddress().getHostAddress()
                : UNKNOWN_CLIENT_IP;
    }

    private String resolveDestination(Route route, URI targetUri) {
        if (targetUri != null) {
            return targetUri.toString();
        }
        return (route != null) ? route.getUri().toString() : UNRESOLVED_DESTINATION;
    }

    private void logCompletedRequest(ServerWebExchange exchange, RouteLogContext context) {
        long duration = System.currentTimeMillis() - context.startTime();
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        log.info(LOG_COMPLETED_REQUEST,
                context.correlationId(), context.method(), context.fullPath(), context.routeId(),
                statusCode != null ? statusCode : UNKNOWN_STATUS,
                duration);
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;
    }
}
