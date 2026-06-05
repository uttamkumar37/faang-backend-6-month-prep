# Kafka Lag Debugging

## Purpose
Diagnose consumer lag, rebalance storms, poison messages, and throughput bottlenecks.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Metrics To Check
- Consumer group lag by partition.
- Records consumed per second.
- Processing time per message.
- Rebalance count and duration.
- DLQ rate and retry topic depth.

## Commands
```bash
kafka-consumer-groups --bootstrap-server <broker> --describe --group <group>
kafka-topics --bootstrap-server <broker> --describe --topic <topic>
```

## Logs To Inspect
- Consumer exceptions and poison payloads.
- Rebalance logs.
- Downstream timeout logs.
- Offset commit failures.

## Root Cause Patterns
- Consumer processing slower than producer rate.
- Hot partition due to bad key.
- Poison message retrying forever.
- Downstream dependency latency.
- Frequent rebalances from max.poll.interval.ms breaches.

## Fix Strategy
Mitigate by scaling consumers up to partition count, pausing bad partitions, moving poison records to DLQ, or reducing producer rate. Fix handler latency, partition key, retry policy, and idempotency.

## Prevention Strategy
Lag alerts by partition, poison-message DLQ, retry budget, idempotent consumers, and load tests with realistic partition keys.

## Interview Questions
- What would you check first and why?
- How do you distinguish mitigation from root-cause fix?
- What metric or alert would prevent recurrence?

## Common Mistakes
- Changing multiple variables before isolating the symptom.
- Ignoring recent deploys and configuration changes.
- Declaring root cause without evidence from metrics, dumps, traces, or query plans.

## Self-Check
- [ ] I can name the first three metrics for this incident type.
- [ ] I know the JVM or dependency command to collect evidence.
- [ ] I can propose one immediate mitigation and one durable prevention.

## Practical Example
Example: Lag grows only on partition 3. Key distribution shows one merchant id owns 60 percent of traffic. Fix by changing partition key strategy and adding per-tenant throttling.
