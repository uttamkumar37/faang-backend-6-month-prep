# Message Queues & Kafka Internals

## Why Message Queues?

- **Decoupling**: producer doesn't know about consumers.
- **Buffering**: absorb traffic spikes. Producer at 10K/sec; consumer at 5K/sec — queue absorbs burst.
- **Durability**: persist messages until consumer processes them.
- **Fan-out**: one event → multiple independent consumers.

---

## Kafka Core Concepts

### Logical Model

```
Topic: "order-events"
  Partition 0: [msg0][msg1][msg4][msg7]   ← offset 0,1,4,7
  Partition 1: [msg2][msg3][msg5]         ← offset 0,1,2
  Partition 2: [msg6][msg8][msg9]         ← offset 0,1,2
```

- **Topic**: logical stream name.
- **Partition**: ordered, immutable log on disk. Unit of parallelism.
- **Offset**: sequential ID of a message within a partition.
- **Segment**: physical file on disk (default 1 GB). Old segments deleted by retention policy.

### Producer → Partition Assignment

```
No key:         round-robin across partitions
With key:       partition = hash(key) % N
                → same key always → same partition → ordering guaranteed per key

Use case: user_id as key → all events for a user go to same partition → ordered processing
```

### Consumer Groups

```
Topic: "payments" (3 partitions)
Consumer Group A (payment-processor): 3 consumers, one per partition
Consumer Group B (analytics): 2 consumers, two partitions → one consumer

  Partition 0 → Consumer A1
  Partition 1 → Consumer A2
  Partition 2 → Consumer A3

  Partition 0,1 → Consumer B1
  Partition 2   → Consumer B2

Each group maintains its own offset. Consumers don't interfere.
```

### Replication

```
Replication factor = 3:
  Partition 0 leader: Broker 1
  Partition 0 follower: Broker 2, Broker 3

Producer writes to leader, followers replicate.
ISR (In-Sync Replicas): brokers that are caught up with leader.
min.insync.replicas=2: require 2 acks before producer gets success.
```

---

## Delivery Semantics

| Semantic | How | Risk |
|---|---|---|
| At most once | ACK before processing | Data loss on crash |
| At least once | ACK after processing | Duplicate processing |
| Exactly once | Idempotent producer + transactional API | More complex |

### Idempotent Producer

```
Producer assigns: producerId + sequence number per message.
Broker deduplicates: retried message with same (producerId, seqNo) → ignore.
acks=all + enable.idempotence=true
```

### Exactly-Once with Transactions

```java
producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("output", key, processedValue));
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

---

## Key Configuration Parameters

### Producer

```
acks=all                  - wait for all ISR replicas 
retries=Integer.MAX_VALUE - retry on transient errors
max.in.flight.requests.per.connection=1  - strict ordering (or 5 with idempotent)
batch.size=32768          - batch messages before sending (32 KB)
linger.ms=5               - wait up to 5ms to fill batch (latency vs throughput)
compression.type=lz4      - compress batches
```

### Consumer

```
auto.offset.reset=earliest    - start from beginning if no committed offset
enable.auto.commit=false      - manual offset commit (safer)
max.poll.records=500          - max records per poll
heartbeat.interval.ms=3000    - frequency of heartbeat to coordinator
session.timeout.ms=30000      - time before consumer considered dead
```

---

## Partitioning Strategy

```
Too few partitions: insufficient parallelism
Too many partitions: more overhead (each partition = file handles, memory)

Rule of thumb:
  partitions = max(throughput / single_partition_throughput, num_consumers_peak)

Ordering requirement: partition by entity key (user_id, order_id)
No ordering requirement: round-robin → better load balance
```

---

## Kafka vs RabbitMQ vs SQS

| Feature | Kafka | RabbitMQ | AWS SQS |
|---|---|---|---|
| Model | Log-based | Queue (push) | Queue (pull) |
| Message retention | Days/weeks | Until consumed | Up to 14 days |
| Ordering | Per-partition | Per-queue | FIFO queue only |
| Replay | Yes (seek to offset) | No | No |
| Throughput | Very high (millions/sec) | Moderate | High |
| Consumer model | Pull (poll) | Push | Pull |
| Use case | Event streaming, audit log | Task queues | Decoupled AWS services |

---

## Consumer Lag Monitoring

```
lag = latest_offset - consumer_committed_offset

Alert if lag > threshold or lag growing:
kafka_consumer_lag_seconds{group, topic, partition}

If lag is growing: consumer too slow → scale out consumers or optimize processing
Max consumers useful = num_partitions (can't exceed)
```

---

## Dead Letter Queue (DLQ)

After max retries, move message to DLQ topic for manual inspection.

```java
try {
    processMessage(record);
    consumer.commitSync();
} catch (NonRetryableException e) {
    dlqProducer.send(new ProducerRecord<>("payments-dlq", record.key(), record.value()));
    consumer.commitSync();
} catch (RetryableException e) {
    // Don't commit → message will be redelivered
}
```

---

## Interview Tips

- Distinguish at-least-once vs exactly-once clearly.
- Consumer group = horizontal scaling: max useful consumers = partition count.
- Kafka is a log, not a queue — messages persist and can be replayed.
- Mention consumer lag monitoring as the health check for async systems.
