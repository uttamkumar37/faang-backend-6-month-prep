# REST API Design

## 1. Resource Naming

```
Good REST resource naming:
GET    /users/{id}                    → single user
GET    /users/{id}/orders             → user's orders
POST   /orders                        → create order
PUT    /orders/{id}                   → full replace
PATCH  /orders/{id}                   → partial update
DELETE /orders/{id}                   → delete order

Bad:
GET  /getUser?userId=123              → verb in URL
POST /createOrder                     → verb in URL  
GET  /user_orders/{userId}            → underscore, non-standard
```

### Rules

- Nouns, not verbs. The HTTP method IS the verb.
- Plural for collections: `/orders` not `/order`.
- Lowercase with hyphens: `/order-items` not `/orderItems`.
- Hierarchical for sub-resources: `/customers/{id}/orders`.
- Actions that don't map cleanly: use sub-resource noun or query param:
  - `POST /orders/{id}/cancel` (acceptable for state transitions)
  - `POST /orders/{id}/actions` with body `{"action": "cancel"}` (purist REST)

---

## 2. HTTP Status Codes

| Code | Name | Use case |
|---|---|---|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (add `Location` header with new resource URL) |
| 204 | No Content | Successful DELETE or PATCH with no response body |
| 400 | Bad Request | Validation failure, malformed JSON |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Authenticated but no permission |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate creation, optimistic lock failure |
| 422 | Unprocessable Entity | Semantically invalid (validation passed but business rule failed) |
| 429 | Too Many Requests | Rate limit exceeded (add `Retry-After` header) |
| 500 | Internal Server Error | Unexpected server error |
| 503 | Service Unavailable | Maintenance, circuit breaker open |

---

## 3. Request & Response Design

### Error response standard (RFC 7807 Problem Details)

```json
{
  "type": "https://api.example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request body contains invalid fields",
  "instance": "/orders/4f5e7b2c-...",
  "errors": [
    {"field": "items", "message": "must not be empty"},
    {"field": "customerId", "message": "must not be null"}
  ],
  "traceId": "abc123def456"
}
```

Always include `traceId` — lets clients correlate errors with your distributed traces.

### Successful list response with pagination metadata

```json
{
  "data": [...],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 543,
    "totalPages": 28,
    "hasNext": true,
    "hasPrevious": false
  },
  "links": {
    "self": "/orders?page=0&size=20",
    "next": "/orders?page=1&size=20"
  }
}
```

---

## 4. Versioning

| Strategy | Example | Notes |
|---|---|---|
| URI versioning | `/api/v1/orders` | Most common, clear, cache-friendly |
| Header versioning | `Accept: application/vnd.company.v1+json` | Cleaner URLs, harder to test |
| Query param | `/orders?version=1` | Simple but polluting |

**Recommendation**: URI versioning for public APIs. Never break v1 — maintain it in parallel.

Breaking changes = new version:
- Removing a field
- Changing a field type
- Changing validation rules more strictly
- Changing error response structure

Non-breaking = no new version:
- Adding a new optional field
- Adding a new endpoint
- Relaxing validation

---

## 5. Idempotency

```
Idempotent methods:
GET, PUT, DELETE → same result if called multiple times

Non-idempotent:
POST → creates a new resource each time

Making POST idempotent with idempotency key:
Client: POST /orders
        Idempotency-Key: 4f5e7b2c-a1b2-...

Server:
  1. Look up key in idempotency store (Redis / DB)
  2. If found → return cached response (202 or original 201)
  3. If not found → process, store result keyed by idempotency key
  4. Return result

Purpose: client retries on network failure won't create duplicate orders.
```

---

## 6. Pagination

### Offset-based (simple, less efficient for deep pages)

```
GET /orders?page=0&size=20&sort=createdAt,desc

SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 0
```

Problem: if data is inserted between page reads, items shift and you may skip or repeat rows.

### Cursor-based (stable, recommended for large datasets)

```
GET /orders?cursor=eyJpZCI6Mn0&size=20

SELECT * FROM orders WHERE id > :cursor ORDER BY id ASC LIMIT 20

Response includes:
{
  "data": [...],
  "nextCursor": "eyJpZCI6MjF9"  // base64 encoded last seen cursor
}
```

Cursor-based pagination is O(1) per page (index range scan) vs. offset which is O(n) (full sort + offset).

---

## 7. Caching Headers

```
Response headers:
Cache-Control: max-age=300, private       # cache for 5 min, user-specific
Cache-Control: max-age=3600, public       # cache for 1 hour, CDN-eligible
ETag: "33a64df551425fcc55e4d42a148795d9f25f89d4"
Last-Modified: Wed, 21 Oct 2023 07:28:00 GMT

Client conditional request:
GET /products/123
If-None-Match: "33a64df55..."     → 304 Not Modified if unchanged
If-Modified-Since: Wed, 21 Oct 2023 07:28:00 GMT
```

---

## 8. Rate Limiting Response

```
HTTP/1.1 429 Too Many Requests
Retry-After: 60
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1698912345  (Unix timestamp when limit resets)

{
  "type": "https://api.example.com/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "detail": "You have exceeded 100 requests per minute"
}
```

---

## 9. Interview Q&A

**Q: What is the difference between PUT and PATCH?**  
`PUT` replaces the entire resource — the request body must contain all fields; any fields omitted are set to null/default. `PATCH` applies a partial update — only the fields included in the request body are changed. `PUT` is idempotent (same request → same state). `PATCH` is usually idempotent in practice but technically depends on implementation.

**Q: When should you use cursor-based pagination instead of offset?**  
For any dataset that is frequently written to (new inserts) or is large (millions of rows). Offset pagination is unstable — new records inserted between page reads cause items to shift, leading to duplicates or skipped rows. Offset is also O(n) in the database (the DB must scan and discard the offset rows). Cursor pagination is stable (reads are relative to the last seen item) and O(1) per page with a proper index.

**Q: How do you implement idempotency for a payment creation endpoint?**  
The client generates a UUID `Idempotency-Key` header for each unique payment attempt. The server stores the result of the first successful call keyed by that UUID (in Redis with TTL, or in the DB). On subsequent calls with the same key, the server returns the cached response immediately without processing again. The key expires after a reasonable window (e.g., 24 hours). This means a client can safely retry on network timeout without risk of double-charging.
