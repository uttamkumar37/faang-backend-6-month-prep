# Project 2: Architecture — Order Processing Platform

## High-Level Architecture

```
Mobile App / Web
        │
        ▼
┌────────────────────────┐
│     API Gateway        │
│  Kong / Spring Cloud   │
│  ─ Auth (JWT)          │
│  ─ Rate Limit          │
│  ─ SSL termination     │
└──────────┬─────────────┘
           │
    ┌──────┴────────────────────────────────────┐
    ▼              ▼                 ▼           ▼
Order-Svc    Inventory-Svc    Payment-Svc    Search-Svc
(Port 8080)  (Port 8081)      (Port 8082)    (Port 8083)
    │              │                │
    └──────────────┴────────────────┘
                   │
                   ▼
           Kafka Cluster (3 brokers)
           Topics:
           ├── order-events
           ├── inventory-events
           ├── payment-events
           └── notification-events
                   │
         ┌─────────┼──────────────┐
         ▼         ▼              ▼
  Notification  Analytics     Search
   Service      Service      Indexer
   (Kafka       (ClickHouse  (Elasticsearch)
    Consumer)    Writer)
```

---

## Saga Orchestration Flow

```
Client → Order Service
  1. Validate request
  2. BEGIN TRANSACTION:
       INSERT orders (status=PLACED)
       INSERT outbox_events (ORDER_PLACED)
     COMMIT
  3. Outbox Poller → Kafka: order-events topic
  
Saga Orchestrator (Order Service, Kafka consumer):
  Step 1: POST inventory-service/reserve
    ↓ success → INSERT outbox(INVENTORY_RESERVED)
    ↓ failure → INSERT outbox(ORDER_CANCELLED)
    
  Step 2: POST payment-service/charge (idempotency-key: orderId)
    ↓ success → INSERT outbox(PAYMENT_CONFIRMED)
    ↓ failure → 
        POST inventory-service/release  ← compensate
        INSERT outbox(ORDER_CANCELLED)

  Step 3: POST notification-service (async, fire-and-forget)
  Step 4: Mark order status = PAYMENT_CONFIRMED
```

---

## Data Flow: Flash Sale Flow (10K orders/sec)

```
10K concurrent order requests
        │
        ▼
API Gateway: rate limit per user (10 req/sec)
        │
        ▼
Order Service: 20 pod instances (HPA)
  - Virtual threads handle 10K concurrent -> no thread pool exhaustion
        │
        ▼
Redis Cache: check stock_cache:{product_id}
  - If cached stock < 0 → reject immediately (no DB hit)
  - Cache TTL: 60 seconds (replenished by inventory events)
        │ cache hit with stock
        ▼
PostgreSQL (inventory): optimistic locking UPDATE
  - ~3K TPS capacity per node with connection pooling
  - 8 read replicas for inventory reads
        │
        ▼
Kafka: order-events → downstream processing at own pace
```

---

## Database Sharding Strategy

Orders table will grow unbounded. Shard by user_id hash for even distribution.

```
Shard key: user_id (orders always queried by user or order_id)
Shard 0: user_id hash 0–25%
Shard 1: user_id hash 25–50%
Shard 2: user_id hash 50–75%
Shard 3: user_id hash 75–100%

Cross-shard query: ORDER BY created_at (admin reports) → Elasticsearch
```

---

## Observability

```
Distributed Tracing (Jaeger):
  traceId propagated via HTTP headers through all service hops.
  Useful for debugging why order-123 failed at payment step.

Key Metrics:
  order_placement_rate          req/sec
  order_success_rate            %
  saga_compensation_rate        %  (should be <1% normally)
  inventory_reservation_miss    count (product sold out)
  payment_failure_rate          % by PSP error code
  kafka_consumer_lag            monitor per consumer group
  
Business Alerts:
  order_success_rate < 99% for 5 min → SEV2 (revenue impact)
  saga_compensation_rate > 5% → SEV3 (likely payment PSP issue)
```

---

## Scaling Decisions

| Component | Scaling Strategy |
|---|---|
| Order Service | Horizontal (HPA), 2-20 pods |
| Inventory Service | Horizontal, read replicas for stock checks |
| Payment Service | Horizontal, bulkhead per PSP provider |
| Kafka | 12 partitions for order-events (= max consumer parallelism) |
| PostgreSQL | Primary + 3 read replicas; PgBouncer connection pool |
| Elasticsearch | 3 shards, 1 replica; update_by_query for order status changes |

---

## Trade-offs

| Decision | Why it helps | What it costs |
|---|---|---|
| Central saga orchestration | Easier debugging and compensation visibility | More orchestration logic in one place |
| PostgreSQL plus outbox | Strong transactional behavior for core writes | Requires CDC or poller infrastructure |
| Async search and analytics updates | Keeps checkout path lean | Eventual consistency for secondary views |
| Optimistic locking for inventory | High concurrency with less lock contention | More retry logic during hotspots |

## Failure modes and mitigations

- Kafka lag spike: autoscale consumers and monitor lag per group.
- Payment PSP outage: open bulkhead or circuit breaker and move to compensation path.
- Inventory contention on hot SKU: reject fast or queue rather than oversell.
- Duplicate event delivery: enforce idempotency at consumers and payment boundary.

## What to measure in a real implementation

- Accepted order latency and failure rate by cause.
- Compensation rate and compensation completion latency.
- Inventory conflict rate for hot products.
- Kafka lag and outbox publish delay.
