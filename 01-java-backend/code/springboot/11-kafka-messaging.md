# Spring Kafka — Messaging From Basics to Production

---

## 1. Kafka Fundamentals

```
Kafka Cluster
├── Broker 1          ← stores data, handles producer/consumer connections
├── Broker 2
└── Broker 3

Topic: "orders"
├── Partition 0 (leader: Broker 1, replicas: 2,3)
│   [0: Order{id=1}] [1: Order{id=3}] [2: Order{id=5}]  ← offsets
├── Partition 1 (leader: Broker 2, replicas: 3,1)
│   [0: Order{id=2}] [1: Order{id=4}]
└── Partition 2 (leader: Broker 3, replicas: 1,2)
    [0: Order{id=6}]

Consumer Group: "order-processors"
├── Consumer A → reads Partition 0
├── Consumer B → reads Partition 1
└── Consumer C → reads Partition 2
```

**Key concepts**:
- **Topic**: logical channel (like a table in a DB)
- **Partition**: ordered, immutable log within a topic — enables parallelism
- **Offset**: unique sequential ID of a record within a partition
- **Consumer Group**: group of consumers sharing the partitions — each partition goes to exactly one consumer in the group
- **Replication factor**: how many copies of each partition exist across brokers
- **ISR (In-Sync Replicas)**: replicas that are caught up with the leader — `acks=all` waits for all ISR replicas

---

## 2. Spring Kafka Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all           # wait for all ISR replicas before acknowledging
      retries: 3          # retry 3 times on transient failures
      properties:
        enable.idempotence: true        # exactly-once delivery guarantee for producer
        max.in.flight.requests.per.connection: 5  # required for idempotence
        
    consumer:
      group-id: order-processors
      auto-offset-reset: earliest       # start from beginning if no committed offset
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.model"
      enable-auto-commit: false         # manual commit for reliability
```

---

## 3. Producer — Sending Messages

```java
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    // Synchronous send — blocks until broker acknowledges
    public void sendSync(OrderEvent event) {
        try {
            SendResult<String, OrderEvent> result =
                kafkaTemplate.send("orders", event.getOrderId(), event).get(5, TimeUnit.SECONDS);
            log.info("Sent to partition {} offset {}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } catch (ExecutionException e) {
            throw new KafkaSendException("Failed to send order event", e.getCause());
        }
    }

    // Asynchronous send — non-blocking, callback on completion
    public void sendAsync(OrderEvent event) {
        kafkaTemplate.send("orders", event.getOrderId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send event for order {}", event.getOrderId(), ex);
                    // Alert, persist failed event to retry table, etc.
                } else {
                    log.debug("Sent to partition {} offset {}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }

    // Sending with headers (for tracing, routing)
    public void sendWithHeaders(OrderEvent event, String correlationId) {
        ProducerRecord<String, OrderEvent> record = new ProducerRecord<>(
            "orders", null, event.getOrderId(), event,
            List.of(new RecordHeader("X-Correlation-Id", correlationId.getBytes()))
        );
        kafkaTemplate.send(record);
    }
}
```

**Producer configuration tuning**:
```yaml
producer:
  batch-size: 65536          # 64KB — batch messages before sending (throughput vs latency)
  linger-ms: 10              # wait up to 10ms for more records to batch (default 0 = no wait)
  compression-type: lz4      # compress batches: none|gzip|snappy|lz4|zstd
  buffer-memory: 33554432    # 32MB total buffer; block if full
```

---

## 4. Consumer — `@KafkaListener`

```java
@Component
public class OrderEventConsumer {

    // Basic listener
    @KafkaListener(topics = "orders", groupId = "order-processors")
    public void handleOrder(OrderEvent event) {
        processOrder(event);
    }

    // Access full record metadata
    @KafkaListener(topics = "orders")
    public void handleWithMetadata(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "X-Correlation-Id", required = false) String correlationId) {
        log.info("Processing event from partition={} offset={}", partition, offset);
        processOrder(event);
    }

    // Manual acknowledgment — most reliable mode
    @KafkaListener(topics = "orders", containerFactory = "manualAckListenerContainerFactory")
    public void handleWithManualAck(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
        try {
            processOrder(record.value());
            ack.acknowledge(); // commit offset AFTER successful processing
        } catch (RecoverableException e) {
            // Don't acknowledge — offset not committed → message will be retried
            log.warn("Transient failure for order {}, will retry", record.value().getOrderId());
        }
    }

    // Batch processing — process multiple records at once for throughput
    @KafkaListener(topics = "analytics-events", containerFactory = "batchListenerContainerFactory")
    public void handleBatch(List<ConsumerRecord<String, AnalyticsEvent>> records) {
        List<AnalyticsEvent> events = records.stream().map(ConsumerRecord::value).toList();
        analyticsService.bulkInsert(events);
    }
}
```

---

## 5. Container Factory Configuration

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> manualAckListenerContainerFactory(
            ConsumerFactory<String, OrderEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties()
               .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // 3 consumer threads (make sure ≤ num partitions)

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AnalyticsEvent> batchListenerContainerFactory(
            ConsumerFactory<String, AnalyticsEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, AnalyticsEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true); // enable batch mode

        return factory;
    }
}
```

**Acknowledgment modes**:
| Mode | When offset is committed | Use case |
|---|---|---|
| `RECORD` | After each record is processed | Safest, lowest throughput |
| `BATCH` | After all records in a poll batch are processed | Balanced |
| `MANUAL` | When `ack.acknowledge()` is called + periodically | Custom control |
| `MANUAL_IMMEDIATE` | When `ack.acknowledge()` is called immediately | Exact control |
| `COUNT` | After N records | Throughput optimization |

---

## 6. Consumer Group Rebalancing

When a consumer joins or leaves a group, partitions are **rebalanced** across consumers:

```
Before rebalance (3 consumers, 3 partitions):
  Consumer A → Partition 0
  Consumer B → Partition 1
  Consumer C → Partition 2

Consumer C crashes → rebalance triggers:
  Consumer A → Partitions 0, 2
  Consumer B → Partition 1

New consumer D joins → rebalance triggers:
  Consumer A → Partition 0
  Consumer B → Partition 1
  Consumer D → Partition 2
```

**Partition assignment strategies**:
- `RangeAssignor` (default): assigns contiguous ranges per topic — can be uneven with multiple topics
- `RoundRobinAssignor`: round-robin across all topics — more even distribution
- `StickyAssignor`: minimizes partition movement during rebalancing — preserves assignments where possible
- `CooperativeStickyAssignor` (recommended for production): incremental rebalancing — only reassigns changed partitions, consumers never stop processing during rebalance

```yaml
spring:
  kafka:
    consumer:
      properties:
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
        session.timeout.ms: 30000       # consumer considered dead if no heartbeat in 30s
        heartbeat.interval.ms: 10000    # send heartbeat every 10s (should be < 1/3 of session timeout)
        max.poll.interval.ms: 300000    # 5 minutes max between polls (increase for slow processing)
```

---

## 7. Exactly-Once Semantics

```
Producer delivery guarantees:
  at-most-once:  send() without retry → can lose messages on failure
  at-least-once: retry on failure → duplicates possible
  exactly-once:  idempotent producer + transactions

Consumer delivery guarantees:
  at-most-once:  commit before processing → lose on crash
  at-least-once: commit after processing → duplicates on crash
  exactly-once:  transactions + consume-transform-produce
```

**Idempotent producer** (prevents duplicates from retries):
```yaml
producer:
  properties:
    enable.idempotence: true              # each producer gets a unique ID + sequence numbers
    # Kafka broker deduplicates based on (producerId, partitionId, sequenceNumber)
    # Automatically sets: acks=all, retries=MAX_INT, max.in.flight=5
```

**Transactional producer** (exactly-once across multiple topics/partitions):
```java
@Bean
public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
    KafkaTemplate<String, OrderEvent> template = new KafkaTemplate<>(producerFactory());
    template.setTransactionIdPrefix("order-tx-"); // enables transactions
    return template;
}

@Transactional
public void processOrderAndPublish(Order order) {
    // All sends in this @Transactional method are part of one Kafka transaction
    kafkaTemplate.send("orders", order.getId(), new OrderCreatedEvent(order));
    kafkaTemplate.send("inventory", order.getId(), new ReserveInventoryEvent(order));
    // If exception thrown: both sends are rolled back atomically
}
```

---

## 8. Error Handling — Retry and Dead Letter Topic

```java
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Dead Letter Publishing Recoverer: sends failed messages to "<topic>.DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                // Custom DLT name: route based on exception type
                if (ex instanceof ValidationException) {
                    return new TopicPartition(record.topic() + ".validation-errors", record.partition());
                }
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            });

        // Retry with exponential backoff: 1s, 2s, 4s, 8s — then send to DLT
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Don't retry these exceptions (send straight to DLT)
        handler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonParseException.class, // bad JSON → no point retrying
            ValidationException.class                             // bad data → won't improve
        );

        return handler;
    }
}

// Register error handler in container factory
factory.setCommonErrorHandler(errorHandler);

// DLT listener — process failed messages (alert, manual review, remediation)
@KafkaListener(topics = "orders.DLT", groupId = "dlt-handler")
public void handleDLT(ConsumerRecord<String, OrderEvent> record,
                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exMessage) {
    log.error("DLT message received from topic={} partition={} offset={}: {}",
        record.topic(), record.partition(), record.offset(), exMessage);
    dlqAlertService.alert(record);
}
```

---

## 9. Consumer Lag Monitoring

Consumer lag = messages produced but not yet consumed. Monitor via:

```bash
# Check consumer group lag (CLI)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group order-processors

# Output:
# GROUP             TOPIC   PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order-processors  orders  0          1234            1240            6
# order-processors  orders  1          5678            5680            2

# Lag = LOG-END-OFFSET - CURRENT-OFFSET (total = 8 in this example)
```

Expose lag as a Micrometer metric via `KafkaConsumerMetrics` (auto-configured in Spring Boot).

---

## 10. Testing with Embedded Kafka

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 3,
    topics = {"orders", "orders.DLT"},
    brokerProperties = {"auto.create.topics.enable=false"}
)
class OrderConsumerTest {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private OrderEventConsumer consumer; // the consumer being tested

    @Autowired
    private EmbeddedKafkaBroker embeddedBroker;

    @Test
    void shouldProcessOrderCreatedEvent() throws Exception {
        OrderEvent event = new OrderEvent("order-1", "user-1", Status.CREATED);

        kafkaTemplate.send("orders", event.getOrderId(), event).get();

        // Wait for consumer to process (with timeout)
        await().atMost(10, SECONDS)
               .until(() -> orderRepository.findById("order-1").isPresent());

        Order saved = orderRepository.findById("order-1").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Status.CREATED);
    }
}
```

---

## 11. Interview Q&A

**Q: How does Kafka guarantee message ordering?**  
Kafka guarantees ordering **within a single partition**. Messages sent with the same key are routed to the same partition (via hash of the key), so all messages for a given entity (e.g., all events for `orderId=123`) arrive in order. There is no ordering guarantee across partitions. If strict global ordering is required, use a single partition — but this eliminates horizontal scaling. For most use cases, per-entity ordering (key-based routing) is sufficient and allows parallelism.

**Q: What is the difference between at-least-once and exactly-once in Kafka?**  
At-least-once: commit offsets only after successfully processing a message. On failure, the consumer restarts and reprocesses the message — guaranteed delivery but possible duplicates. Exactly-once: uses idempotent producers (deduplication via sequence numbers) combined with transactional commits (atomic write to output topic + offset commit). This prevents both loss and duplicates. Exactly-once has higher latency and complexity — for most systems, at-least-once with idempotent consumers (dedup by message ID) is the practical choice.

**Q: What happens when a consumer is too slow (consumer lag grows)?**  
As lag grows, consumers fall further behind producers. If lag exceeds the retention period, messages are deleted before they can be consumed — data loss. Solutions: (1) increase consumer concurrency (`setConcurrency(N)`) up to the number of partitions; (2) increase partitions (requires rebalance); (3) optimize consumer processing (batch, async, or async processing with manual ack); (4) scale horizontally by adding consumer instances to the group. Monitor lag continuously with metrics and alert before it reaches the retention window.
