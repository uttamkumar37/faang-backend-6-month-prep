package systemdesign.idempotency;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Idempotency & Transactional Outbox Patterns
 * Covers:
 *  - Idempotency key store (prevents duplicate payment/order processing)
 *  - Transactional outbox pattern (atomic DB write + event publish)
 *  - Saga orchestrator skeleton
 */
public class IdempotencyAndOutboxPatterns {

    // ─────────────────────────────────────────────
    // 1. IDEMPOTENCY KEY STORE
    // Store request fingerprint + response. Same key = return cached response.
    // In production: use Redis SETNX with TTL.
    // ─────────────────────────────────────────────

    record IdempotencyEntry(String key, String requestHash, String responseBody,
                            int httpStatus, Instant createdAt, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    static class IdempotencyKeyStore {
        private final Map<String, IdempotencyEntry> store = new ConcurrentHashMap<>();
        private final long ttlSeconds;

        IdempotencyKeyStore(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        /**
         * Try to reserve an idempotency key.
         * Returns empty if this is a new request (should proceed with processing).
         * Returns existing entry if key was already used.
         */
        public Optional<IdempotencyEntry> getOrReserve(String key, String requestHash) {
            IdempotencyEntry existing = store.get(key);

            if (existing != null) {
                if (existing.isExpired()) {
                    store.remove(key);
                    // Fall through to re-process (TTL expired, safe to retry)
                } else if (!existing.requestHash().equals(requestHash)) {
                    // Same key, different payload — error condition
                    throw new IllegalStateException(
                        "Idempotency key reused with different request body: " + key);
                } else {
                    return Optional.of(existing); // return cached response
                }
            }

            // Reserve the key with pending state (response empty until processing done)
            Instant now = Instant.now();
            IdempotencyEntry pending = new IdempotencyEntry(
                key, requestHash, null, 0, now, now.plusSeconds(ttlSeconds));
            store.put(key, pending);
            return Optional.empty(); // caller should process
        }

        public void complete(String key, String responseBody, int httpStatus) {
            IdempotencyEntry existing = store.get(key);
            if (existing == null) throw new IllegalStateException("Key not found: " + key);

            IdempotencyEntry completed = new IdempotencyEntry(
                key, existing.requestHash(), responseBody, httpStatus,
                existing.createdAt(), existing.expiresAt());
            store.put(key, completed);
        }
    }

    // ─────────────────────────────────────────────
    // 2. TRANSACTIONAL OUTBOX PATTERN
    // Problem: dual write — can't atomically write to DB and publish to Kafka.
    // Solution: write event to outbox table in SAME transaction as business data.
    //           Background worker publishes from outbox to Kafka.
    // ─────────────────────────────────────────────

    record Order(String orderId, long userId, double amount, String status) {}

    record OutboxEvent(String id, String aggregateType, String aggregateId,
                       String eventType, String payload, boolean published,
                       Instant createdAt) {}

    // Simulates a single DB transaction containing both order + outbox write
    static class OrderRepository {
        private final Map<String, Order> orders = new ConcurrentHashMap<>();
        private final List<OutboxEvent> outbox = new CopyOnWriteArrayList<>();

        // Simulate DB transaction: both writes happen atomically
        public synchronized Order createOrderWithOutboxEvent(Order order) {
            // In real code: this is a single @Transactional method in Spring
            orders.put(order.orderId(), order);

            OutboxEvent event = new OutboxEvent(
                UUID.randomUUID().toString(), "Order", order.orderId(),
                "ORDER_CREATED",
                """
                {"orderId":"%s","userId":%d,"amount":%.2f}
                """.formatted(order.orderId(), order.userId(), order.amount()).strip(),
                false,
                Instant.now()
            );
            outbox.add(event);

            System.out.printf("[DB] Saved order %s + outbox event (same transaction)%n",
                order.orderId());
            return order;
        }

        public List<OutboxEvent> findUnpublished(int limit) {
            return outbox.stream()
                .filter(e -> !e.published())
                .limit(limit)
                .toList();
        }

        public synchronized void markPublished(String eventId) {
            outbox.replaceAll(e -> e.id().equals(eventId)
                ? new OutboxEvent(e.id(), e.aggregateType(), e.aggregateId(),
                                  e.eventType(), e.payload(), true, e.createdAt())
                : e);
        }
    }

    // Simulates Kafka producer
    interface EventPublisher {
        void publish(String topic, String key, String payload);
    }

    // Outbox poller — runs every N ms, publishes unpublished events
    static class OutboxPoller {
        private final OrderRepository repo;
        private final EventPublisher publisher;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        OutboxPoller(OrderRepository repo, EventPublisher publisher) {
            this.repo = repo;
            this.publisher = publisher;
        }

        public void start(long intervalMs) {
            scheduler.scheduleAtFixedRate(this::poll, 0, intervalMs, TimeUnit.MILLISECONDS);
        }

        private void poll() {
            List<OutboxEvent> events = repo.findUnpublished(50);
            for (OutboxEvent event : events) {
                try {
                    publisher.publish("order-events", event.aggregateId(), event.payload());
                    repo.markPublished(event.id());
                    System.out.printf("[Outbox] Published to Kafka: %s → %s%n",
                        event.eventType(), event.aggregateId());
                } catch (Exception e) {
                    System.err.println("[Outbox] Failed to publish, will retry: " + e.getMessage());
                    // Don't mark published — will retry on next poll
                }
            }
        }

        public void stop() { scheduler.shutdown(); }
    }

    // ─────────────────────────────────────────────
    // 3. SAGA ORCHESTRATOR SKELETON
    // Coordinates multi-step distributed transaction with compensation on failure.
    // ─────────────────────────────────────────────

    enum SagaStatus { STARTED, IN_PROGRESS, COMPLETED, COMPENSATING, FAILED }

    record SagaState(String sagaId, String orderId, SagaStatus status, int currentStep,
                     List<String> completedSteps) {}

    interface SagaStep {
        String name();
        boolean execute(String sagaId, String orderId);
        boolean compensate(String sagaId, String orderId);  // undo on failure
    }

    static class OrderSagaOrchestrator {
        private final List<SagaStep> steps;
        private final Map<String, SagaState> sagas = new ConcurrentHashMap<>();

        OrderSagaOrchestrator(List<SagaStep> steps) {
            this.steps = steps;
        }

        public boolean startSaga(String orderId) {
            String sagaId = "saga-" + orderId;
            sagas.put(sagaId, new SagaState(sagaId, orderId, SagaStatus.STARTED, 0, new ArrayList<>()));

            System.out.printf("[Saga] Starting saga for order %s%n", orderId);

            for (int i = 0; i < steps.size(); i++) {
                SagaStep step = steps.get(i);
                updateStep(sagaId, i, SagaStatus.IN_PROGRESS);

                boolean success = step.execute(sagaId, orderId);
                if (success) {
                    sagas.get(sagaId).completedSteps().add(step.name());
                    System.out.printf("[Saga] Step %s: SUCCESS%n", step.name());
                } else {
                    System.out.printf("[Saga] Step %s: FAILED — compensating%n", step.name());
                    compensate(sagaId, orderId, i);
                    return false;
                }
            }

            updateStep(sagaId, steps.size() - 1, SagaStatus.COMPLETED);
            System.out.printf("[Saga] Order %s saga COMPLETED%n", orderId);
            return true;
        }

        private void compensate(String sagaId, String orderId, int failedStep) {
            updateStep(sagaId, failedStep, SagaStatus.COMPENSATING);
            // Compensate in reverse order (from failedStep - 1 back to 0)
            SagaState state = sagas.get(sagaId);
            List<String> completed = new ArrayList<>(state.completedSteps());
            Collections.reverse(completed);

            for (String completedStepName : completed) {
                SagaStep step = steps.stream()
                    .filter(s -> s.name().equals(completedStepName))
                    .findFirst().orElseThrow();
                boolean compensated = step.compensate(sagaId, orderId);
                System.out.printf("[Saga] Compensate %s: %s%n", step.name(),
                    compensated ? "OK" : "FAILED (needs manual intervention)");
            }
            updateStep(sagaId, 0, SagaStatus.FAILED);
        }

        private void updateStep(String sagaId, int step, SagaStatus status) {
            SagaState old = sagas.get(sagaId);
            sagas.put(sagaId, new SagaState(sagaId, old.orderId(), status, step, old.completedSteps()));
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Idempotency Key Store ===");
        IdempotencyKeyStore idempotency = new IdempotencyKeyStore(3600);
        String key = "client-uuid-123";
        String hash = "sha256-of-request-body";

        Optional<IdempotencyEntry> first = idempotency.getOrReserve(key, hash);
        System.out.println("First call: " + (first.isEmpty() ? "PROCESS (new)" : "CACHED"));
        idempotency.complete(key, "{\"paymentId\":\"pay-456\"}", 200);

        Optional<IdempotencyEntry> second = idempotency.getOrReserve(key, hash);
        System.out.println("Second call (duplicate): " + second.map(e -> "CACHED → " + e.responseBody()).orElse("NEW"));

        System.out.println("\n=== Transactional Outbox ===");
        OrderRepository repo = new OrderRepository();
        EventPublisher fakeKafka = (topic, k, payload) ->
            System.out.printf("[Kafka] topic=%s key=%s payload=%s%n", topic, k, payload);

        OutboxPoller poller = new OutboxPoller(repo, fakeKafka);

        // Create order (outbox event written atomically with order)
        repo.createOrderWithOutboxEvent(new Order("ORD-001", 42L, 99.99, "PENDING"));
        repo.createOrderWithOutboxEvent(new Order("ORD-002", 43L, 149.00, "PENDING"));

        System.out.println("\nStarting outbox poller...");
        poller.start(100);
        Thread.sleep(300);
        poller.stop();

        System.out.println("\n=== Saga Orchestrator ===");
        List<SagaStep> sagaSteps = List.of(
            new SagaStep() {
                public String name() { return "CreateOrder"; }
                public boolean execute(String sagaId, String orderId) {
                    System.out.println("  Creating order record in DB...");
                    return true;
                }
                public boolean compensate(String sagaId, String orderId) {
                    System.out.println("  Cancelling order record...");
                    return true;
                }
            },
            new SagaStep() {
                public String name() { return "ReserveInventory"; }
                public boolean execute(String sagaId, String orderId) {
                    System.out.println("  Reserving inventory...");
                    return true;
                }
                public boolean compensate(String sagaId, String orderId) {
                    System.out.println("  Releasing inventory reservation...");
                    return true;
                }
            },
            new SagaStep() {
                public String name() { return "ChargePayment"; }
                public boolean execute(String sagaId, String orderId) {
                    System.out.println("  Charging payment... FAILED (insufficient funds)");
                    return false;  // simulate failure
                }
                public boolean compensate(String sagaId, String orderId) {
                    System.out.println("  Refunding payment...");
                    return true;
                }
            }
        );

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(sagaSteps);
        System.out.println("Result: " + (orchestrator.startSaga("ORD-003") ? "SUCCESS" : "FAILED + COMPENSATED"));
    }
}
