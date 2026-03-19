# Distributed Transactions

## The Problem

In a microservices architecture, a business transaction spans multiple services, each with its own database.

```
Place Order:
  1. Order Service     → INSERT order
  2. Inventory Service → UPDATE stock
  3. Payment Service   → charge card
  4. Notification      → send email

All must succeed or all must roll back.
But they're separate databases — ACID across services isn't possible.
```

---

## Two-Phase Commit (2PC)

### Phase 1: Prepare

Coordinator asks all participants: "Can you commit?"

```
Coordinator → "PREPARE" → Order DB, Inventory DB, Payment Service
                ← "YES"    ← "YES"               ← "YES"
```

### Phase 2: Commit or Abort

If all YES → Coordinator sends COMMIT. Else → sends ABORT.

```
Coordinator → "COMMIT" → all participants
```

**Problems with 2PC:**
- **Blocking**: If coordinator crashes between phases, participants block forever holding locks.
- **Latency**: 2 network round trips per transaction.
- **Availability**: If any participant is down, all block.

Used in: XA transactions (JDBC), some relational DB clusters. Rare in microservices.

---

## Saga Pattern

Break distributed transaction into local transactions with compensating transactions for rollback.

### Choreography-based Saga

Each service listens for events and reacts:

```
Order Placed Event
  → Inventory Service: reserve stock → Stock Reserved Event
       → Payment Service: charge card → Payment Completed Event
            → Notification: send email → Done

Failure:
  Payment Failed Event
    → Inventory Service: release reservation (compensating)
    → Order Service: cancel order (compensating)
```

**Pros**: No coordinator dependency.  
**Cons**: Complex event chains; hard to debug; risk of partial failures.

### Orchestration-based Saga (Recommended)

A central coordinator (saga orchestrator) tells each service what to do:

```
SagaOrchestrator:
  1. OrderService.create()      → SUCCESS
  2. InventoryService.reserve() → SUCCESS
  3. PaymentService.charge()    → FAILURE
  4. InventoryService.release() → compensate
  5. OrderService.cancel()      → compensate
```

```java
// Saga state stored in DB
enum SagaStep { ORDER_CREATED, STOCK_RESERVED, PAYMENT_FAILED, COMPENSATING, DONE }

// Each step: local DB transaction + outbox event
```

**Pros**: Centralized control; easier to monitor.  
**Cons**: Orchestrator is a bottleneck; single responsibility violation if too complex.

---

## Transactional Outbox Pattern

Solves the dual-write problem: write to DB + publish event atomically (without 2PC).

```
Problem:
  db.save(order);            // succeeds
  kafka.publish(event);      // crashes — event lost!
  // OR
  kafka.publish(event);      // succeeds  
  db.save(order);            // crashes — order lost but event sent!
```

### Solution: Outbox Table

Same transaction writes both the data AND the event to DB:

```sql
BEGIN TRANSACTION;
  INSERT INTO orders (id, ...) VALUES (...);
  INSERT INTO outbox (id, event_type, payload, published) 
    VALUES (uuid(), 'ORDER_CREATED', '{"orderId":...}', false);
COMMIT;
```

Background poller or CDC reads unpublished outbox entries → publishes → marks published.

```java
@Scheduled(fixedDelay = 100)
void pollOutbox() {
    List<OutboxEvent> events = outboxRepo.findUnpublished(100);
    for (OutboxEvent e : events) {
        kafkaTemplate.send(e.getTopic(), e.getPayload());
        outboxRepo.markPublished(e.getId());
    }
}
```

---

## Change Data Capture (CDC) with Debezium

Alternative to polling: Debezium reads PostgreSQL WAL (write-ahead log) and streams changes to Kafka.

```
PostgreSQL WAL → Debezium Connector → Kafka
                                       → consumers

Config: debezium source connector on outbox table
Result: every INSERT to outbox → Kafka event automatically
```

Pros: no polling; no performance impact on DB; exactly-once delivery with Kafka.

---

## Idempotency

Saga steps must be idempotent: retrying a step that already succeeded must not cause harm.

```java
// Idempotency key = sagaId + stepName
String idempotencyKey = sagaId + ":reserve-stock";
if (idempotencyStore.alreadyProcessed(idempotencyKey)) {
    return cachedResult(idempotencyKey);
}
// Process...
idempotencyStore.store(idempotencyKey, result);
```

---

## Comparison Table

| Approach | Consistency | Availability | Complexity | Use Case |
|---|---|---|---|---|
| 2PC | Strong | Low (blocking) | Medium | Short-lived txns, same DC |
| Saga (choreo) | Eventual | High | High (event chains) | Simple flows |
| Saga (orchestrated) | Eventual | High | Medium | Complex flows |
| Outbox + CDC | Eventual | High | Medium | Any microservice write |

---

## Interview Tips

- 2PC is theoretical knowledge; Saga is the practical answer.
- Always mention compensating transactions for Saga rollback.
- Outbox = the key to removing dual-write risk — draw it out.
- Idempotency at every step is non-negotiable for Saga.
