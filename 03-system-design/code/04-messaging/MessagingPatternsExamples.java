package systemdesign.messaging;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * Messaging Patterns — Kafka-inspired implementation
 *
 * Topics:
 *  1. In-memory broker — topic, partitions, producer, consumer
 *  2. Consumer groups — independent offset tracking per group
 *  3. At-least-once delivery — retry on failure
 *  4. Dead Letter Queue — move poison messages aside
 *  5. Exactly-once simulation — idempotent consumer with processed-event log
 *  6. Fan-out — single event, multiple consumer groups
 *  7. Priority queue — VIP vs standard message processing
 *  8. Competing consumers — work queue load distribution
 */
public class MessagingPatternsExamples {

    // ─── Domain events ───────────────────────────────────────────────────────
    record Message(String id, String topic, int partition, long offset,
                   String key, Object payload, Instant timestamp) {}

    // ─────────────────────────────────────────────────────────────────────────
    // 1. IN-MEMORY BROKER (Kafka-inspired)
    // ─────────────────────────────────────────────────────────────────────────

    static class Broker {
        // topic → partition → ordered log of messages
        private final Map<String, List<List<Message>>> topic = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong[]>        offsets = new ConcurrentHashMap<>();

        void createTopic(String name, int partitionCount) {
            List<List<Message>> partitions = new ArrayList<>();
            AtomicLong[] partOffsets = new AtomicLong[partitionCount];
            for (int i = 0; i < partitionCount; i++) {
                partitions.add(new CopyOnWriteArrayList<>());
                partOffsets[i] = new AtomicLong(0);
            }
            topic.put(name, partitions);
            offsets.put(name, partOffsets);
            System.out.printf("  [Broker] topic '%s' created with %d partitions%n", name, partitionCount);
        }

        void send(String topicName, String key, Object payload) {
            List<List<Message>> partitions = topic.getOrDefault(topicName, List.of());
            if (partitions.isEmpty()) throw new IllegalArgumentException("Unknown topic: " + topicName);

            // Hash key to partition (same key → same partition → ordering guaranteed)
            int partIdx = key == null ? 0 : Math.abs(key.hashCode()) % partitions.size();
            AtomicLong off = offsets.get(topicName)[partIdx];
            long offset = off.getAndIncrement();

            Message msg = new Message(
                UUID.randomUUID().toString(), topicName, partIdx, offset,
                key, payload, Instant.now()
            );
            partitions.get(partIdx).add(msg);
        }

        // Poll messages from a partition starting at the given offset
        List<Message> poll(String topicName, int partition, long fromOffset, int maxMessages) {
            List<List<Message>> partitions = topic.getOrDefault(topicName, List.of());
            if (partition >= partitions.size()) return List.of();
            List<Message> log = partitions.get(partition);
            List<Message> result = new ArrayList<>();
            for (Message m : log) {
                if (m.offset() >= fromOffset && result.size() < maxMessages) result.add(m);
            }
            return result;
        }

        long latestOffset(String topicName, int partition) {
            return offsets.get(topicName)[partition].get();
        }

        int partitionCount(String topicName) {
            return topic.getOrDefault(topicName, List.of()).size();
        }
    }

    static void basicBrokerDemo() {
        System.out.println("=== 1. In-Memory Broker ===");

        Broker broker = new Broker();
        broker.createTopic("order-events", 3);

        // Order service publishes events
        String[] users = {"alice", "bob", "carol", "alice", "bob"};
        String[] events = {"placed", "placed", "placed", "shipped", "cancelled"};
        for (int i = 0; i < users.length; i++) {
            broker.send("order-events", users[i], Map.of("user", users[i], "event", events[i]));
        }

        // Show partition distribution (same key → same partition)
        System.out.println("  Messages per partition:");
        for (int p = 0; p < 3; p++) {
            List<Message> messages = broker.poll("order-events", p, 0, 100);
            System.out.printf("    Partition %d: %s%n", p,
                messages.stream().map(m -> m.key() + "/" + ((Map<?,?>)m.payload()).get("event")).toList());
        }
        // alice messages always in same partition → ordering guarantee per user
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CONSUMER GROUPS — independent offset tracking
    // ─────────────────────────────────────────────────────────────────────────

    static class ConsumerGroup {
        private final String             groupId;
        private final Broker             broker;
        private final String             topicName;
        // Per-partition committed offsets for this group
        private final Map<Integer, Long> committedOffsets = new ConcurrentHashMap<>();

        ConsumerGroup(String groupId, Broker broker, String topicName) {
            this.groupId   = groupId;
            this.broker    = broker;
            this.topicName = topicName;
        }

        List<Message> poll(int partition, int maxMessages) {
            long fromOffset = committedOffsets.getOrDefault(partition, 0L);
            return broker.poll(topicName, partition, fromOffset, maxMessages);
        }

        void commitOffset(int partition, long offset) {
            committedOffsets.put(partition, offset + 1); // next to consume
        }

        long lag(int partition) {
            long committed = committedOffsets.getOrDefault(partition, 0L);
            return broker.latestOffset(topicName, partition) - committed;
        }
    }

    static void consumerGroupDemo() {
        System.out.println("\n=== 2. Consumer Groups ===");

        Broker broker = new Broker();
        broker.createTopic("payments", 2);

        for (int i = 1; i <= 6; i++) {
            broker.send("payments", "user-" + (i % 3), Map.of("amount", i * 10));
        }

        // Two independent consumer groups — each tracks own offsets
        ConsumerGroup cg1 = new ConsumerGroup("billing-service",      broker, "payments");
        ConsumerGroup cg2 = new ConsumerGroup("notification-service",  broker, "payments");

        // billing reads partition 0
        System.out.println("  billing-service reads partition 0:");
        List<Message> batch = cg1.poll(0, 5);
        batch.forEach(m -> System.out.println("    " + m.key() + " → " + m.payload()));
        if (!batch.isEmpty()) cg1.commitOffset(0, batch.get(batch.size() - 1).offset());

        // notification reads partition 0 independently (sees same messages)
        System.out.println("  notification-service reads partition 0 (independent):");
        List<Message> batch2 = cg2.poll(0, 2); // only takes 2
        batch2.forEach(m -> System.out.println("    " + m.key() + " → " + m.payload()));
        if (!batch2.isEmpty()) cg2.commitOffset(0, batch2.get(batch2.size() - 1).offset());

        // Check lag
        System.out.printf("  notification-service lag on partition 0: %d%n", cg2.lag(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. AT-LEAST-ONCE DELIVERY — retry on failure
    // ─────────────────────────────────────────────────────────────────────────

    static class AtLeastOnceConsumer {
        private final String topic;
        private final Broker broker;
        private final Map<Integer, Long> offsets = new ConcurrentHashMap<>();
        private final int maxRetries;
        private final long retryDelayMs;

        AtLeastOnceConsumer(Broker broker, String topic, int maxRetries, long retryDelayMs) {
            this.broker      = broker;
            this.topic       = topic;
            this.maxRetries  = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        void process(int partition, Consumer<Message> handler, Consumer<Message> dlqHandler) {
            long fromOffset = offsets.getOrDefault(partition, 0L);
            List<Message> messages = broker.poll(topic, partition, fromOffset, 10);

            for (Message msg : messages) {
                boolean processed = false;
                for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
                    try {
                        handler.accept(msg);
                        processed = true;
                        break;
                    } catch (RuntimeException e) {
                        System.out.printf("  [Consumer] msg=%s attempt=%d failed: %s%n",
                            msg.id().substring(0, 8), attempt, e.getMessage());
                        if (attempt <= maxRetries) {
                            try { Thread.sleep(retryDelayMs * attempt); }
                            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    }
                }
                if (processed) {
                    // Commit AFTER processing — at-least-once (crash before commit = reprocess)
                    offsets.put(partition, msg.offset() + 1);
                } else {
                    // Send to DLQ after all retries exhausted
                    dlqHandler.accept(msg);
                    offsets.put(partition, msg.offset() + 1); // still commit to unblock
                    System.out.println("  [DLQ] message sent to dead letter queue: " + msg.id().substring(0, 8));
                }
            }
        }
    }

    static void atLeastOnceDemo() {
        System.out.println("\n=== 3. At-Least-Once + DLQ ===");

        Broker broker = new Broker();
        broker.createTopic("orders", 1);
        broker.createTopic("orders-dlq", 1);

        // Normal messages
        broker.send("orders", "u1", Map.of("orderId", "o1", "amount", 100));
        broker.send("orders", "u2", Map.of("orderId", "o2", "amount", -1)); // bad — will fail
        broker.send("orders", "u3", Map.of("orderId", "o3", "amount", 200));

        AtomicInteger processingAttempts = new AtomicInteger(0);

        AtLeastOnceConsumer consumer = new AtLeastOnceConsumer(broker, "orders", 2, 20);
        consumer.process(0,
            msg -> {
                int n = processingAttempts.incrementAndGet();
                @SuppressWarnings("unchecked")
                int amount = ((Map<String, Integer>) msg.payload()).get("amount");
                if (amount < 0) throw new IllegalArgumentException("Negative amount: " + amount);
                System.out.printf("  [OK] processed order %s (attempt %d)%n",
                    ((Map<?,?>) msg.payload()).get("orderId"), n);
            },
            dlqMsg -> broker.send("orders-dlq", dlqMsg.key(), dlqMsg.payload())
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. IDEMPOTENT CONSUMER — exactly-once semantics via deduplication
    // ─────────────────────────────────────────────────────────────────────────

    static class IdempotentProcessor {
        // Processed event IDs — in production: stored in DB with TTL or Bloom filter
        private final Set<String> processedIds = ConcurrentHashMap.newKeySet();
        private int totalProcessed = 0;
        private int duplicatesSkipped = 0;

        void process(Message msg) {
            String eventId = msg.id();

            // Check-then-act must be atomic in real systems
            // Use: INSERT INTO processed_events (id) VALUES (?) ON CONFLICT DO NOTHING
            if (!processedIds.add(eventId)) {
                duplicatesSkipped++;
                System.out.println("  [Idempotent] SKIP duplicate: " + eventId.substring(0, 8));
                return;
            }

            // Process
            totalProcessed++;
            System.out.println("  [Idempotent] PROCESS: " + msg.payload());
        }

        void stats() {
            System.out.printf("  Processed=%d Duplicates skipped=%d%n", totalProcessed, duplicatesSkipped);
        }
    }

    static void idempotentConsumerDemo() {
        System.out.println("\n=== 4. Idempotent Consumer ===");

        IdempotentProcessor processor = new IdempotentProcessor();

        // Simulate receiving same message twice (at-least-once re-delivery)
        Message m1 = new Message("msg-abc-12345", "orders", 0, 0L, "u1",
            Map.of("orderId", "o1"), Instant.now());
        Message m2 = new Message("msg-def-67890", "orders", 0, 1L, "u2",
            Map.of("orderId", "o2"), Instant.now());
        Message m1Dup = m1; // same message redelivered

        processor.process(m1);
        processor.process(m2);
        processor.process(m1Dup); // duplicate → should be skipped
        processor.process(m2);    // duplicate → should be skipped
        processor.stats();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. FAN-OUT — one event, multiple independent consumers
    // ─────────────────────────────────────────────────────────────────────────

    static class EventBus {
        private final Map<String, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

        <T> void subscribe(String topic, Consumer<T> handler) {
            subscribers.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>())
                .add(event -> {
                    @SuppressWarnings("unchecked")
                    T typed = (T) event;
                    handler.accept(typed);
                });
        }

        void publish(String topic, Object event) {
            System.out.println("  [Bus] publishing to '" + topic + "': " + event);
            List<Consumer<Object>> handlers = subscribers.getOrDefault(topic, List.of());
            // In Kafka: each consumer group reads independently; no shared handler list
            // Here simulated with multiple subscriptions
            handlers.forEach(h -> {
                try { h.accept(event); }
                catch (Exception e) { System.err.println("  [Bus] handler error: " + e.getMessage()); }
            });
        }
    }

    record OrderPlaced(String orderId, String userId, double amount) {}

    static void fanOutDemo() {
        System.out.println("\n=== 5. Fan-out (one event, multiple consumers) ===");

        EventBus bus = new EventBus();

        // Multiple services subscribe independently
        bus.subscribe("order.placed", (OrderPlaced e) ->
            System.out.printf("  [InventoryService] reserve stock for order %s%n", e.orderId()));
        bus.subscribe("order.placed", (OrderPlaced e) ->
            System.out.printf("  [EmailService] send confirmation to user %s%n", e.userId()));
        bus.subscribe("order.placed", (OrderPlaced e) ->
            System.out.printf("  [AnalyticsService] record revenue $%.2f%n", e.amount()));
        bus.subscribe("order.placed", (OrderPlaced e) ->
            System.out.printf("  [FraudService] scan order %s for fraud%n", e.orderId()));

        // One publish triggers all subscribers
        bus.publish("order.placed", new OrderPlaced("ORD-001", "alice", 199.99));

        System.out.println("\n  Fan-out patterns:");
        System.out.println("    Kafka: 4 consumer groups → each reads same topic independently");
        System.out.println("    SNS: one publish → fan-out to SQS queues, email, Lambda");
        System.out.println("    RabbitMQ: fanout exchange → binds to multiple queues");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. COMPETING CONSUMERS — work queue with multiple workers
    // ─────────────────────────────────────────────────────────────────────────

    static class WorkQueue<T> {
        private final BlockingQueue<T> queue;
        private final List<Thread>    workers = new ArrayList<>();
        private volatile boolean      running = true;

        WorkQueue(int capacity) { this.queue = new LinkedBlockingQueue<>(capacity); }

        void submit(T task) throws InterruptedException {
            queue.put(task); // blocks if queue is full (backpressure)
        }

        void startWorkers(int count, Consumer<T> handler) {
            for (int i = 0; i < count; i++) {
                final int workerIdx = i;
                Thread worker = Thread.ofVirtual().start(() -> {
                    while (running || !queue.isEmpty()) {
                        try {
                            T task = queue.poll(100, TimeUnit.MILLISECONDS);
                            if (task != null) {
                                System.out.printf("  [Worker-%d] processing: %s%n", workerIdx, task);
                                Thread.sleep(10); // simulate work
                                handler.accept(task);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                workers.add(worker);
            }
        }

        void stop() throws InterruptedException {
            running = false;
            for (Thread w : workers) w.join(2000);
        }
    }

    static void competingConsumersDemo() throws Exception {
        System.out.println("\n=== 6. Competing Consumers (Work Queue) ===");

        WorkQueue<String> queue = new WorkQueue<>(100);
        AtomicInteger processed = new AtomicInteger(0);

        queue.startWorkers(3, task -> processed.incrementAndGet());

        // Produce 20 tasks
        for (int i = 1; i <= 20; i++) {
            queue.submit("email-task-" + i);
        }

        queue.stop();
        System.out.printf("  Total tasks processed by 3 workers: %d/20%n", processed.get());

        System.out.println("\n  Competing Consumer benefits:");
        System.out.println("    Scale workers = scale throughput");
        System.out.println("    Worker crashes → other workers continue");
        System.out.println("    Worker busy → queue absorbs burst");
        System.out.println("    In Kafka: add partitions + consumers in same group");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. PRIORITY QUEUE — VIP vs standard processing
    // ─────────────────────────────────────────────────────────────────────────

    static class PriorityMessageQueue {
        record PrioritizedMessage(int priority, String id, Object payload)
            implements Comparable<PrioritizedMessage> {
            @Override public int compareTo(PrioritizedMessage other) {
                return Integer.compare(other.priority, this.priority); // higher priority = first
            }
        }

        private final PriorityBlockingQueue<PrioritizedMessage> queue = new PriorityBlockingQueue<>();

        void send(int priority, String id, Object payload) {
            queue.offer(new PrioritizedMessage(priority, id, payload));
        }

        PrioritizedMessage poll() throws InterruptedException {
            return queue.poll(100, TimeUnit.MILLISECONDS);
        }

        int size() { return queue.size(); }
    }

    static void priorityQueueDemo() throws Exception {
        System.out.println("\n=== 7. Priority Queue ===");

        PriorityMessageQueue pq = new PriorityMessageQueue();

        // Mix of standard and VIP messages
        pq.send(1, "std-1",  Map.of("type", "standard", "user", "alice"));
        pq.send(1, "std-2",  Map.of("type", "standard", "user", "bob"));
        pq.send(10, "vip-1", Map.of("type", "VIP",      "user", "enterprise-a"));
        pq.send(1, "std-3",  Map.of("type", "standard", "user", "carol"));
        pq.send(10, "vip-2", Map.of("type", "VIP",      "user", "enterprise-b"));
        pq.send(5, "prem-1", Map.of("type", "premium",  "user", "premium-x"));

        System.out.println("  Consuming in priority order:");
        while (pq.size() > 0) {
            PriorityMessageQueue.PrioritizedMessage msg = pq.poll();
            if (msg != null) {
                System.out.printf("  [Priority=%2d] %s%n", msg.priority(), msg.payload());
            }
        }
        System.out.println("\n  Pattern: use multiple queues (vip-queue, standard-queue)");
        System.out.println("  Worker checks vip-queue first, falls back to standard-queue");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. CONSUMER LAG MONITORING
    // ─────────────────────────────────────────────────────────────────────────

    static void consumerLagDemo() {
        System.out.println("\n=== 8. Consumer Lag Monitoring ===");

        Broker broker = new Broker();
        broker.createTopic("high-volume-topic", 3);

        // Producer fast — produces 100 messages
        for (int i = 0; i < 100; i++) {
            broker.send("high-volume-topic", "key-" + (i % 10), "event-" + i);
        }

        // Slow consumer — only processed 60 messages
        ConsumerGroup slowGroup = new ConsumerGroup("slow-service", broker, "high-volume-topic");
        for (int p = 0; p < 3; p++) {
            List<Message> batch = slowGroup.poll(p, 20); // takes 20 per partition
            if (!batch.isEmpty()) slowGroup.commitOffset(p, batch.get(batch.size() - 1).offset());
        }

        // Report lag
        long totalLag = 0;
        System.out.println("  Consumer group 'slow-service' lag:");
        for (int p = 0; p < 3; p++) {
            long partLag = slowGroup.lag(p);
            totalLag += partLag;
            System.out.printf("    Partition %d: lag=%d%n", p, partLag);
        }
        System.out.printf("  Total consumer lag: %d messages behind%n", totalLag);
        System.out.println("\n  Alert: consumer lag > threshold → scale up consumers");
        System.out.println("         In Kafka: offset = latest_offset - committed_offset");
        System.out.println("         Monitored via: kafka.consumer.group.lag (JMX/MSK CloudWatch)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        basicBrokerDemo();
        consumerGroupDemo();
        atLeastOnceDemo();
        idempotentConsumerDemo();
        fanOutDemo();
        competingConsumersDemo();
        priorityQueueDemo();
        consumerLagDemo();
        System.out.println("\n=== All messaging pattern demos completed ===");
    }
}
