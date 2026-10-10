package com.occupify.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.occupify.gateway.config.SecurityProperties;
import com.occupify.gateway.enums.UserRole;
import com.occupify.gateway.exception.code.CommonErrorCode;
import com.occupify.gateway.exception.handler.GlobalErrorWebExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AuthenticationGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String BEARER_PREFIX = "Bearer ";

    public static final String ROLE_ADMIN = UserRole.ADMIN.name();

    private static final String MSG_MISSING_AUTH_HEADER = "Missing or invalid Authorization header";
    private static final String MSG_INVALID_JWT_TOKEN = "Invalid or expired JWT token";
    private static final String MSG_ACCESS_DENIED_ADMIN = "Access denied: ADMIN role required";

    private final JwtUtils jwtUtils;
    private final GlobalErrorWebExceptionHandler errorHandler;
    private final SecurityProperties securityProperties;

    @Autowired
    public AuthenticationGatewayFilter(
            JwtUtils jwtUtils,
            GlobalErrorWebExceptionHandler errorHandler,
            SecurityProperties securityProperties) {
        this.jwtUtils = jwtUtils;
        this.errorHandler = errorHandler;
        this.securityProperties = securityProperties;
    }

    public AuthenticationGatewayFilter(JwtUtils jwtUtils, GlobalErrorWebExceptionHandler errorHandler) {
        this(jwtUtils, errorHandler, new SecurityProperties());
    }

    public AuthenticationGatewayFilter(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this(jwtUtils, new GlobalErrorWebExceptionHandler(objectMapper), new SecurityProperties());
    }

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String rawPath = request.getURI().getPath();
        String path = normalizePath(rawPath);

        if (isOptionsRequest(request)) {
            return chain.filter(exchange);
        }

        if (isWhitelisted(path)) {
            // Sanitize and strip any client-supplied spoofed identity headers
            ServerHttpRequest sanitizedRequest = stripIdentityHeaders(request);
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        return authenticateAndAuthorize(exchange, chain, path);
    }

    private Mono<Void> authenticateAndAuthorize(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = errorHandler.resolveCorrelationId(exchange);

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[Corr-{}] Missing or malformed Authorization header for path: {}", correlationId, path);
            return errorHandler.writeError(exchange, HttpStatus.UNAUTHORIZED, MSG_MISSING_AUTH_HEADER, CommonErrorCode.AUTH_003.getCode());
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        Optional<UserClaims> userClaimsOpt = jwtUtils.extractUserClaims(token);
        if (userClaimsOpt.isEmpty()) {
            log.warn("[Corr-{}] Invalid or expired JWT token for path: {}", correlationId, path);
            return errorHandler.writeError(exchange, HttpStatus.UNAUTHORIZED, MSG_INVALID_JWT_TOKEN, CommonErrorCode.AUTH_005.getCode());
        }

        UserClaims claims = userClaimsOpt.get();
        if (isAdminRouteRestricted(path, claims.role())) {
            log.warn("[Corr-{}] Access denied to admin endpoint [{}] for user [id={}, email={}] with role [{}]",
                    correlationId, path, claims.userId(), claims.email(), claims.role());
            return errorHandler.writeError(exchange, HttpStatus.FORBIDDEN, MSG_ACCESS_DENIED_ADMIN, CommonErrorCode.USER_007.getCode());
        }

        ServerHttpRequest mutatedRequest = mutateRequestWithClaims(request, claims);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isOptionsRequest(ServerHttpRequest request) {
        return HttpMethod.OPTIONS.equals(request.getMethod());
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String cleaned = StringUtils.cleanPath(rawPath);
        return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }

    private boolean isWhitelisted(String path) {
        return matchesPath(path, securityProperties.getWhitelistedPaths());
    }

    private boolean isAdminRouteRestricted(String path, String role) {
        boolean isRestricted = matchesPath(path, securityProperties.getAdminRestrictedPaths());
        return isRestricted && !UserRole.isAdmin(role);
    }

    private boolean matchesPath(String path, List<String> patterns) {
        if (path == null || patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
            if (pattern.endsWith("/**")) {
                String basePath = pattern.substring(0, pattern.length() - 3);
                if (pathMatcher.match(basePath, path)) {
                    return true;
                }
            }
            if (pattern.endsWith("/") && (path + "/").startsWith(pattern)) {
                return true;
            }
            if (!pattern.contains("*") && (path.equals(pattern) || path.startsWith(pattern.endsWith("/") ? pattern : pattern + "/"))) {
                return true;
            }
        }
        return false;
    }

    private ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(HEADER_USER_ID);
                    httpHeaders.remove(HEADER_USER_EMAIL);
                    httpHeaders.remove(HEADER_USER_ROLE);
                })
                .build();
    }

    private ServerHttpRequest mutateRequestWithClaims(ServerHttpRequest request, UserClaims claims) {
        return request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.set(HEADER_USER_ID, claims.userId() != null ? claims.userId() : "");
                    httpHeaders.set(HEADER_USER_EMAIL, claims.email() != null ? claims.email() : "");
                    httpHeaders.set(HEADER_USER_ROLE, claims.role() != null ? claims.role() : "");
                })
                .build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
