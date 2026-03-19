# Message Queues & Kafka Deep Dive

## Why Async Messaging?

Synchronous (REST):
```
Order Service → [HTTP] → Inventory Service → [HTTP] → Notification Service
                250ms         150ms                         100ms
                              
Total: 500ms, and if Notification Service is down → order fails
```

Asynchronous (Queue):
```
Order Service → [Queue] → Inventory Service
             ↓
          [Queue] → Notification Service

Order Service returns in 5ms. Others process independently.
```

**Benefits:** decoupling, buffering, fan-out, durability, backpressure.

---

## Kafka Architecture

### Core Concepts

```
Broker cluster:
  Broker-1 ──── Partition 0 (leader) ──── Replica on Broker-2
  Broker-2 ──── Partition 1 (leader) ──── Replica on Broker-3
  Broker-3 ──── Partition 2 (leader) ──── Replica on Broker-1

Topic: "order-events"
  Partition 0: [msg0][msg1][msg4][msg7]  ← ordered within partition
  Partition 1: [msg2][msg3][msg5]
  Partition 2: [msg6][msg8][msg9]

Producer → assigns to partition via: round-robin | key hash | custom
Consumer → subscribes to topic, reads by offset
```

### Consumer Groups

```
Topic: "orders" (3 partitions)
Consumer Group: "inventory-service"

  CG "inventory-service":
    Consumer-A → Partition 0
    Consumer-B → Partition 1, Partition 2  (one consumer can take multiple partitions)

  CG "notification-service" (independent offset tracking):
    Consumer-X → Partition 0, 1, 2  (reads same messages at own pace)

Rule: at most 1 consumer per partition per group.
      More consumers than partitions → idle consumers.
```

### Offset Management

```
Consumer commits offset after processing:
  [msg0][msg1][msg2][msg3][msg4]
              ↑
          committed offset=2

After consumer restart → reads from offset 2 (msg2 onwards)

At-most-once:  commit offset BEFORE processing → may lose if crash
At-least-once: commit offset AFTER processing  → may duplicate if crash before commit
Exactly-once:  transactional produce/commit     → Kafka 0.11+, idempotent producer
```

---

## Message Delivery Guarantees

| Guarantee | How | Risk |
|-----------|-----|------|
| At-most-once | Pre-commit before process | Message loss on crash |
| At-least-once | Post-commit after process | Duplicate processing |
| Exactly-once | Kafka transactions + idempotent producer | Higher latency, complexity |

### Making At-Least-Once Safe

Since at-least-once is the practical default, make consumers **idempotent**:

```
Operation idempotency:
  Receive order-placed event twice:
    Bad:  INSERT INTO orders → duplicate row
    Good: INSERT INTO orders ... ON CONFLICT (event_id) DO NOTHING
    Good: IF NOT EXISTS(SELECT 1 FROM processed_events WHERE id = event_id)
            THEN process AND INSERT INTO processed_events
```

---

## Kafka Internals

### Log Storage

```
Each partition is an append-only log on disk:
  Segment 0: /kafka/orders-0/00000000000000000000.log
  Segment 1: /kafka/orders-0/00000000000000010000.log
  Segment 2: /kafka/orders-0/00000000000000020000.log  ← active

.index file:    maps offset → file position (sparse, every N bytes)
.timeindex file: maps timestamp → offset

Reads: binary search on .index → seek to position → read from log

Retention:
  By time:  log.retention.hours=168     (7 days)
  By size:  log.retention.bytes=1GB per partition
```

### Replication

```
Leader handles all reads and writes.
Followers replicate from leader (ISR = In-Sync Replicas).

acks=0:  no wait       → fastest, may lose data
acks=1:  leader wrote  → lose data if leader fails before replication
acks=-1: all ISR wrote → strongest durability, higher latency

min.insync.replicas=2 → at least 2 replicas must confirm write
```

### Partitioning Keys

```
No key (null)    → round-robin across partitions (max parallelism)
With key         → hash(key) % numPartitions → same key → same partition
                   → ordering guaranteed per key
                   → e.g., user_id ensures same user's events stay ordered

Choosing partition count:
  More partitions = more parallelism = more consumers possible
  But: more overhead for Zookeeper/KRaft, more file handles
  
Rule of thumb: start at max(target_consumer_count, topic_throughput / partition_throughput)
```

---

## Dead Letter Queue (DLQ)

```
Normal flow:
  Producer → [orders-topic] → Consumer → process → commit offset

Failed message flow:
  Consumer → process fails → retry 3x with backoff → still fails
  → publish to [orders-dlq] → commit original offset → processing continues

DLQ processing:
  Separate consumer group reads DLQ
  Alert on DLQ growth
  Manual inspection and replay after fix

Why DLQ: prevents one bad message from blocking all subsequent messages
```

---

## Kafka vs Other Systems

| System | Best for | Retention | Consumer model |
|--------|----------|-----------|----------------|
| Kafka | Event streaming, log replay | Days/weeks (configurable) | Pull, offset-based |
| RabbitMQ | Task queues, routing | Until consumed | Push, ACK-based |
| SQS | AWS-native simple queuing | Up to 14 days | Pull, visibility timeout |
| Pub/Sub | Google Cloud, serverless | 7 days max | Push or pull |

---

## Producer Batching & Compression

```
batch.size=16KB            → accumulate messages before sending
linger.ms=5                → wait up to 5ms for batch to fill
compression.type=lz4       → compress batch (lz4=fast, snappy=balanced, gzip=highest ratio)

Result: 1 network request per batch instead of 1 per message
        Throughput: 10K msg/s → 100 batches/s (100x fewer requests)
        
buffer.memory=32MB         → total producer buffer; block on full if block.on.buffer.full=true
max.block.ms=60000         → throw TimeoutException if buffer full for 60s
```

---

## Interview Quick Reference

| Question | Answer |
|----------|--------|
| Ordering within topic? | Only within a partition. Use key to colocate related events. |
| How to replay all events? | Reset consumer group offset to earliest (--reset-offsets) |
| What is ISR? | In-Sync Replicas — replicas fully caught up to leader. acks=-1 waits for all ISR. |
| What happens when leader fails? | Controller elects new leader from ISR. With acks=1, in-flight writes may be lost. |
| How to handle DLQ? | Separate topic, separate consumer group, alert + manual replay |
| Kafka vs RabbitMQ? | Kafka: event streaming, high throughput, replay. RabbitMQ: routing, task queues. |
| How to achieve exactly-once? | Idempotent producer (enable.idempotence=true) + transactional API |
| Consumer lag? | Track via consumer_offsets topic or JMX metric records-lag-max |
