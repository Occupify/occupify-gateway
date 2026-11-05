# Occupify API Gateway - Specification

> [!NOTE]
> **Base Port & System Entry Point:**
> - External Gateway Port: `8180` (Single ingress entry point for all frontend and external clients)
> - Downstream Services:
>   - Identity / Auth: `http://localhost:8181`
>   - Core Marketplace: `http://localhost:8182`
>   - Payment: `http://localhost:8183`
>   - Notification: `http://localhost:8184`
>   - Administration: `http://localhost:8185`
> - Architecture: Spring Cloud Gateway (Spring Boot 3 / Project Reactor WebFlux)

---

## Standard Response Envelopes

The Gateway adheres to and propagates standard Occupify response envelopes:

### Single Data Response (`SingleResponse<T>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

### Resource Created Response (`CreatedResponse<T>`)
```json
{
  "statusCode": 201,
  "message": "Resource created successfully",
  "data": { ... }
}
```

### Paginated Data Response (`PageResponse<T>`)
```json
{
  "statusCode": 200,
  "message": "Operation completed successfully",
  "data": [ ... ],
  "paging": {
    "page": 0,
    "limit": 20,
    "total": 100,
    "totalPages": 5
  }
}
```

### Gateway Error & Fallback Response (`ErrorResponse`)
```json
{
  "statusCode": 503,
  "errorCode": "SYS_008",
  "message": "Core marketplace service is temporarily unavailable. Please try again later."
}
```

---

## 1. Overview & Core Responsibilities

The **Occupify API Gateway** (`occupify-gateway`) is the central reverse-proxy, authentication router, and edge resilience layer:
1. **JWT Authentication & Validation:** Intercepts incoming requests, parses `Authorization: Bearer <token>`, validates HMAC-SHA512 (`HS512`) signatures and expiration, and denies unauthenticated access with `401 UNAUTHORIZED`.
2. **Context Propagation & Spoofing Protection:**
   - Strips any untrusted client-supplied identity headers (`X-User-Id`, `X-User-Email`, `X-User-Role`) on whitelisted endpoints.
   - Decodes authenticated JWT claims and safely injects verified headers for downstream services:
     - `X-User-Id`: Authenticated user UUID string
     - `X-User-Email`: Authenticated user email
     - `X-User-Role`: Authenticated user role (`USER` or `ADMIN`)
3. **Correlation Tracking (`X-Correlation-Id`):** Automatically assigns or propagates an `X-Correlation-Id` across all requests and responses for end-to-end distributed tracing.
4. **Resilience & Fault Tolerance:**
   - Resilience4j Circuit Breakers prevent cascading failures across downstream services.
   - Retry filters with exponential backoff on idempotent `GET` and `HEAD` operations.
   - Seamless internal redirection to `/fallback/*` endpoints when downstream instances fail or time out.
5. **Role-Based Routing Security:** Restricts administrative route patterns (`/admin/**`, `/audit-logs/**`, `/reports/**`) exclusively to tokens bearing `X-User-Role: ADMIN`. Rejects unauthorized roles with `403 FORBIDDEN` (`USER_007`).

---

## 2. Downstream Route Matrix

| Route ID | Path Predicates | Downstream Target | Retry Policy | Circuit Breaker |
| :--- | :--- | :--- | :--- | :--- |
| `auth-service` | `/auth/**`, `/account/**`, `/users/**`, `/user/**` | `http://localhost:8181` | Disabled (Auth mutations) | Gateway passthrough |
| `core-service` | `/projects/**`, `/applications/**`, `/reviews/**`, `/bids/**` | `http://localhost:8182` | 3 retries (GET/HEAD, 100-1000ms) | `coreCircuitBreaker` |
| `payment-service` | `/contracts/**`, `/wallets/**`, `/payments/**` | `http://localhost:8183` | 3 retries (GET/HEAD, 100-1000ms) | `paymentCircuitBreaker` |
| `notification-service` | `/notifications/**` | `http://localhost:8184` | 3 retries (GET/HEAD, 100-1000ms) | `notificationCircuitBreaker` |
| `administration-service`| `/admin/**`, `/audit-logs/**`, `/reports/**` | `http://localhost:8185` | 3 retries (GET/HEAD, 100-1000ms) | `adminCircuitBreaker` |

---

## 3. Path Authorization & Security Policies

| Path Pattern | Access Policy | Required Header / Role | Behavior / Error Code |
| :--- | :--- | :--- | :--- |
| `/auth/**` | Public / Whitelisted | None | Client headers stripped, forwarded to Identity. |
| `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/scalar/**`, `/docs/**` | Whitelisted | None | Public API docs and health checks. |
| `/fallback/**` | Internal Whitelist | None | Handled directly by Gateway Fallback Controller. |
| `/user/institutions`, `/users/institutions` | Public / Whitelisted | None | Catalog lookup during onboarding. |
| `/admin/**`, `/audit-logs/**`, `/reports/**` | Admin Restricted | `X-User-Role: ADMIN` | Returns `403 FORBIDDEN` (`USER_007`) if non-admin. |
| All other paths (`/projects/**`, `/contracts/**`, `/wallets/**`, `/notifications/**`) | Authenticated | `Authorization: Bearer <jwt>` | Returns `401 UNAUTHORIZED` (`AUTH_003` or `AUTH_005`) if missing or invalid. |

---

## 4. Gateway Fallback Endpoints (`/fallback/*`)

When a downstream service instance crashes, becomes unresponsive, or trips its Resilience4j circuit breaker, the request is internally routed to `GatewayFallbackController`.

### 4.1 Common Fallback
- **Endpoint:** `GET|POST|PUT|DELETE|PATCH /fallback/common`
- **Description:** Default fallback handler for unspecified downstream services.
- **HTTP Methods:** All methods supported.

### 4.2 Service-Specific Fallbacks
- **Endpoint:** `GET|POST|PUT|DELETE|PATCH /fallback/{service}`
- **Supported Path Variables:** `core`, `payment`, `notification` (or `noti`), `administration` (or `admin`).
- **HTTP Methods:** All methods supported.

#### 4.2.1 Service Unavailable (`503 SERVICE_UNAVAILABLE`)
Triggered when the circuit breaker is in `OPEN` state or downstream connection is refused.
##### Response Headers
```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
X-Correlation-Id: c9a4d8e2-1234-4567-89ab-cdef01234567
```
##### Response Body Example (`ErrorResponse`)
```json
{
  "statusCode": 503,
  "message": "Core marketplace service is temporarily unavailable. Please try again later.",
  "errorCode": "SYS_008"
}
```

#### 4.2.2 Gateway Timeout (`504 GATEWAY_TIMEOUT`)
Triggered when downstream response exceeds `resilience4j.timelimiter.timeoutDuration` (5000ms).
##### Response Headers
```http
HTTP/1.1 504 Gateway Timeout
Content-Type: application/json
X-Correlation-Id: c9a4d8e2-1234-4567-89ab-cdef01234567
```
##### Response Body Example (`ErrorResponse`)
```json
{
  "statusCode": 504,
  "message": "Payment service request timed out. Please try again later.",
  "errorCode": "SYS_009"
}
```

---

## 5. Security Rejection Response Examples

### 5.1 Missing Authorization Header (`401 UNAUTHORIZED`)
Triggered when a protected path is invoked without `Authorization: Bearer <token>`.
```json
{
  "statusCode": 401,
  "message": "Missing or invalid Authorization header",
  "errorCode": "AUTH_003"
}
```

### 5.2 Invalid or Expired Token (`401 UNAUTHORIZED`)
Triggered when the JWT signature is invalid, corrupted, or past expiration.
```json
{
  "statusCode": 401,
  "message": "Invalid or expired JWT token",
  "errorCode": "AUTH_005"
}
```

### 5.3 Admin Privilege Required (`403 FORBIDDEN`)
Triggered when an authenticated user with `X-User-Role: USER` attempts to access administrative routes (`/admin/**`, `/audit-logs/**`, `/reports/**`).
```json
{
  "statusCode": 403,
  "message": "Access denied: ADMIN role required",
  "errorCode": "USER_007"
}
```
