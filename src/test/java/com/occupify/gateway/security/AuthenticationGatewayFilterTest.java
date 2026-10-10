package com.occupify.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.occupify.gateway.filter.CorrelationIdFilter;
import com.occupify.gateway.security.impl.JwtUtilsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationGatewayFilterTest {

    private static final String TEST_SECRET = "occupify-super-secret-jwt-signing-key-for-unit-testing-must-be-at-least-64-bytes-long!";
    private AuthenticationGatewayFilter filter;
    private JwtUtils jwtUtils;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtilsImpl(TEST_SECRET);
        objectMapper = new ObjectMapper();
        filter = new AuthenticationGatewayFilter(jwtUtils, objectMapper);
    }

    private String createTestToken(String email, String userId, String role) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(email)
                .claim(JwtUtils.CLAIM_USER_ID, userId)
                .claim(JwtUtils.CLAIM_ROLE, role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    @Test
    void shouldAllowWhitelistedAuthPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Whitelisted /auth/ endpoint should bypass auth filter");
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAllowWhitelistedActuatorPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Whitelisted /actuator/ endpoint should bypass auth filter");
    }

    @Test
    void shouldAllowWhitelistedFallbackPathWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/fallback/core").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Whitelisted /fallback/ endpoint should bypass auth filter");
    }

    @Test
    void shouldAllowOptionsPreflightRequestWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.OPTIONS, "/projects").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "OPTIONS preflight should bypass auth filter");
    }

    @Test
    void shouldRejectProtectedEndpointWhenAuthorizationHeaderIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/projects/100").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "corr-test-1");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get(), "Chain should NOT be called when Authorization header is missing");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRejectInvalidTokenWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/projects/100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-or-malformed-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get(), "Chain should NOT be called for invalid token");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAuthenticateValidTokenAndInjectHeaders() {
        String email = "john.doe@occupify.com";
        String userId = UUID.randomUUID().toString();
        String role = "USER";
        String validToken = createTestToken(email, userId, role);

        MockServerHttpRequest request = MockServerHttpRequest.get("/projects/100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<String> injectedUserId = new AtomicReference<>();
        AtomicReference<String> injectedEmail = new AtomicReference<>();
        AtomicReference<String> injectedRole = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            injectedUserId.set(ex.getRequest().getHeaders().getFirst(AuthenticationGatewayFilter.HEADER_USER_ID));
            injectedEmail.set(ex.getRequest().getHeaders().getFirst(AuthenticationGatewayFilter.HEADER_USER_EMAIL));
            injectedRole.set(ex.getRequest().getHeaders().getFirst(AuthenticationGatewayFilter.HEADER_USER_ROLE));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Chain should be called for valid JWT token");
        assertEquals(userId, injectedUserId.get());
        assertEquals(email, injectedEmail.get());
        assertEquals(role, injectedRole.get());
    }

    @Test
    void shouldRejectNonAdminForAdminEndpointWith403() {
        String token = createTestToken("user@occupify.com", "123", "USER");

        MockServerHttpRequest request = MockServerHttpRequest.get("/admin/audit-logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get(), "Non-admin user should not access /admin/ route");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldAllowAdminForAdminEndpoint() {
        String adminToken = createTestToken("admin@occupify.com", "999", "ADMIN");

        MockServerHttpRequest request = MockServerHttpRequest.get("/admin/audit-logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get(), "Admin user should access /admin/ route");
    }

    @Test
    void shouldStripSpoofedIdentityHeadersOnWhitelistedRoute() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/auth/login")
                .header(AuthenticationGatewayFilter.HEADER_USER_ID, "hacker-user-id")
                .header(AuthenticationGatewayFilter.HEADER_USER_ROLE, "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<String> passedUserId = new AtomicReference<>();
        AtomicReference<String> passedUserRole = new AtomicReference<>();

        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            passedUserId.set(ex.getRequest().getHeaders().getFirst(AuthenticationGatewayFilter.HEADER_USER_ID));
            passedUserRole.set(ex.getRequest().getHeaders().getFirst(AuthenticationGatewayFilter.HEADER_USER_ROLE));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(passedUserId.get(), "Spoofed X-User-Id must be stripped on whitelisted routes");
        assertNull(passedUserRole.get(), "Spoofed X-User-Role must be stripped on whitelisted routes");
    }

    @Test
    void shouldRejectNonAdminForAuditLogsAndReportsEndpointsWith403() {
        String token = createTestToken("user@occupify.com", "123", "USER");

        for (String restrictedPath : List.of("/audit-logs/recent", "/reports/monthly")) {
            MockServerHttpRequest request = MockServerHttpRequest.get(restrictedPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            GatewayFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            filter.filter(exchange, chain).block();

            assertFalse(chainCalled.get(), "Non-admin user should not access " + restrictedPath);
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(),
                    "Expected 403 Forbidden for restricted path: " + restrictedPath);
        }
    }

    @Test
    void shouldRejectNonAdminForAdminEndpointWithoutTrailingSlashWith403() {
        String token = createTestToken("user@occupify.com", "123", "USER");

        MockServerHttpRequest request = MockServerHttpRequest.get("/admin")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get(), "Non-admin user should not access /admin route without trailing slash");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldPreventPathTraversalBypassAttemptWith401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/../admin/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get(), "Path traversal to protected route must NOT bypass auth filter");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
