# API Design, Gateway Patterns & gRPC

## REST API Design Principles

### Naming Resources

```
Good:
  GET    /users/123/orders         → list orders for user
  POST   /orders                   → create order
  GET    /orders/456               → get order
  PATCH  /orders/456               → partial update
  DELETE /orders/456               → delete
  POST   /orders/456/refunds       → sub-resource action

Bad:
  GET  /getOrder?id=456            → verb in URL
  POST /createUser                 → verb in URL
  GET  /user_order_list/123        → underscore, non-standard nesting
```

### HTTP Status Codes

| Code | Meaning | Use Case |
|---|---|---|
| 200 | OK | Successful GET / PATCH |
| 201 | Created | POST created resource (include Location header) |
| 204 | No Content | DELETE success |
| 400 | Bad Request | Validation error |
| 401 | Unauthorized | Missing/invalid auth token |
| 403 | Forbidden | Valid auth, insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate create, version conflict |
| 422 | Unprocessable | Semantic validation failure |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Error | Unexpected server error |

### Versioning Strategies

```
URL path (most common):      /v1/users, /v2/users
Accept header:               Accept: application/vnd.api+json;version=2
Custom header:               X-API-Version: 2
Subdomain:                   v2.api.company.com
```

URL path preferred: explicit, cache-friendly, easy to test in browser.

---

## API Gateway

Central entry point handling cross-cutting concerns.

```
Client
  ↓
API Gateway
  ├── Auth (JWT validation / OAuth2 token introspection)
  ├── Rate Limiting (per client/route)
  ├── Request routing (path → service)
  ├── Request/response transformation
  ├── SSL termination
  ├── Logging & tracing (inject correlation-id)
  └── Circuit breaker
  ↓
Internal Services (no auth needed — gateway already validated)
```

### Service Mesh vs API Gateway

| Feature | API Gateway | Service Mesh (Istio/Linkerd) |
|---|---|---|
| Purpose | North-south traffic (client → service) | East-west traffic (service → service) |
| Auth | External client auth | mTLS between services |
| Location | Single entry point | Sidecar proxy on every pod |
| Use case | API management | Microservice observability & security |

---

## gRPC

Protocol Buffers + HTTP/2 based RPC framework.

### Proto Definition

```protobuf
syntax = "proto3";

service OrderService {
  rpc GetOrder(GetOrderRequest) returns (Order);
  rpc ListOrders(ListOrdersRequest) returns (stream Order);
  rpc CreateOrder(CreateOrderRequest) returns (Order);
}

message Order {
  string order_id = 1;
  int64 user_id = 2;
  double amount = 3;
  repeated OrderItem items = 4;
}
```

### gRPC vs REST

| Feature | gRPC | REST |
|---|---|---|
| Protocol | HTTP/2 binary | HTTP/1.1 text |
| Serialization | Protobuf (~10x smaller) | JSON (verbose) |
| Streaming | Bidirectional | Response only (SSE) |
| Browser support | Limited (grpc-web) | Native |
| Code gen | Strong (all languages) | OpenAPI codegen |
| Best for | Internal microservices | Public APIs, browsers |
| Latency | Lower | Higher |

### gRPC Status Codes

```
OK, CANCELLED, UNKNOWN, INVALID_ARGUMENT, NOT_FOUND,
ALREADY_EXISTS, PERMISSION_DENIED, RESOURCE_EXHAUSTED (rate limit),
UNIMPLEMENTED, INTERNAL, UNAVAILABLE, UNAUTHENTICATED
```

---

## GraphQL

Client specifies exactly what fields it needs.

```graphql
query {
  user(id: "123") {
    name
    orders(last: 5) {
      id
      amount
      items {
        productName
        quantity
      }
    }
  }
}
```

**Pros**: No over-fetching, no under-fetching. Self-documenting.  
**Cons**: Complex caching (no GET = no HTTP cache). N+1 problem (DataLoader needed). Write operations (mutations) less clear than REST.

---

## Pagination

### Offset Pagination (simple, bad for large datasets)

```
GET /orders?page=5&size=20
-- Problem: INSERT during pagination → items shift, duplicates/skips
```

### Cursor Pagination (production choice)

```
GET /orders?cursor=eyJpZCI6MTAwfQ==&size=20
-- cursor = base64 encoded { id: 100, createdAt: "..." }
-- Always consistent regardless of inserts

Response:
{
  "data": [...],
  "nextCursor": "eyJpZCI6MTIwfQ==",
  "hasMore": true
}
```

---

## Idempotency in APIs

```
POST /payments
Idempotency-Key: client-generated-uuid

Server behavior:
  - First call: process and store (key → response).
  - Duplicate call: return stored response.
  - Key TTL: 24 hours.
```

Store idempotency keys in Redis with TTL. Include request hash to detect changed payload with same key.

---

## Interview Tips

- REST naming rules are frequently tested — know plural nouns, proper HTTP verbs.
- Cursor pagination > offset for any high-volume API.
- gRPC for internal service communication; REST for public APIs.
- API Gateway handles auth, rate limiting, routing — services don't re-implement these.
